/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.tinytemplate;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Templates {

	public final static class VariableExpander {

		public final static class Builder {
			private boolean nullsAreEmpty = true;
			private boolean missingThrowsException = true;
			private Set<Supplier<ResourceBundle>> bundles = new LinkedHashSet<>();
			private Function<String, Boolean> conditionEvaluator;
			private Optional<Logger> logger = Optional.of(defaultStdOutLogger());
			private Function<String, Supplier<?>> variableSupplier;

			public Builder withNullsAsNull() {
				return withNullsAreEmpty(false);
			}

			public Builder withNullsAreEmpty(boolean nullsAreEmpty) {
				this.nullsAreEmpty = nullsAreEmpty;
				return this;
			}

			public Builder withMissingAsNull() {
				return withMissingThrowsException(false);
			}

			public Builder withMissingThrowsException(boolean missingThrowsException) {
				this.missingThrowsException = missingThrowsException;
				return this;
			}

			public Builder withBundles(ResourceBundle... bundles) {
				return withBundles(Arrays.asList(bundles));
			}

			public Builder withBundles(Collection<ResourceBundle> bundles) {
				return withBundleSuppliers(bundles.stream().map(sb -> new Supplier<ResourceBundle>() {
					@Override
					public ResourceBundle get() {
						return sb;
					}

				}).collect(Collectors.toList()));
			}

			public Builder withBundleSuppliers(Collection<? extends Supplier<ResourceBundle>> bundles) {
				this.bundles.addAll(bundles);
				return this;
			}

			public Builder withConditionEvaluator(Function<String, Boolean> conditionEvaluator) {
				this.conditionEvaluator = conditionEvaluator;
				return this;
			}

			public Builder withVariableSupplier(Function<String, Supplier<?>> variableSupplier) {
				this.variableSupplier = variableSupplier;
				return this;
			}

			public Builder withLogger(Logger logger) {
				return withLogger(Optional.of(logger));
			}

			public Builder withLogger(Optional<Logger> logger) {
				this.logger = logger;
				return this;
			}

			public Builder fromSimpleMap(Map<String, ? extends Object> map) {
				withVariableSupplier(k -> {
					var val = map.get(k);
					return val == null ? null : () -> val;
				});
				withConditionEvaluator(k -> eval(map, k));
				return this;
			}

			public VariableExpander build() {
				return new VariableExpander(this);
			}

			private boolean eval(Map<String, ? extends Object> map, String k) {
				var in = map.containsKey(k);
				if (!in)
					return false;
				var val = map.get(k);
				return evalAsCondition(val);
			}

		}

		final static String NAME_REGEXP = "[a-zA-Z_][a-zA-Z\\._\\-0-9]+";

		private final static String VAR_REGEXP = "\\$\\{(.*?)\\}";
		private final static String REGEXP = "([%]?)(" + NAME_REGEXP + ")([:]?)([-=\\?\\+]?)(.*)";
		private final static String TERN_REGEXP = "([^:]*)(:)(.*)";

		private final Pattern exprPattern;
		private final Function<String, Supplier<?>> variableSupplier;
		private final boolean nullsAreEmpty;
		private final boolean missingThrowsException;
		private final Optional<Logger> logger;
		private final Set<Supplier<ResourceBundle>> bundles;
		private final Function<String, Boolean> conditionEvaluator;
		private final Pattern varPattern;
		private final Pattern ternPattern;

		private VariableExpander(Builder bldr) {
			exprPattern = Pattern.compile(REGEXP);
			varPattern = Pattern.compile(VAR_REGEXP);
			ternPattern = Pattern.compile(TERN_REGEXP);

			this.bundles = Collections.unmodifiableSet(new LinkedHashSet<>(bldr.bundles));
			this.logger = bldr.logger;
			this.conditionEvaluator = bldr.conditionEvaluator;
			this.variableSupplier = bldr.variableSupplier;
			this.nullsAreEmpty = bldr.nullsAreEmpty;
			this.missingThrowsException = bldr.missingThrowsException;
		}

		public String process(String text) {
			var matcher = varPattern.matcher(text);
			var builder = new StringBuilder();
			var i = 0;

			while (matcher.find()) {
				var replacement = expand(matcher.group(1));
				builder.append(text.substring(i, matcher.start()));
				builder.append(replacement);
				i = matcher.end();
			}

			builder.append(text.substring(i, text.length()));
			return builder.toString();
		}

		public String expand(String input) {
			logger.ifPresent(l -> l.debug("Expanding ''{0}''", input));

			var mtchr = exprPattern.matcher(input);
			if (mtchr.find()) {
				var intro = mtchr.group(1);
				if (intro.equals("%")) {
					/* i18n */
					var word = mtchr.group(2);
					for (var bundle : bundles) {
						try {
							return bundle.get().getString(word);
						} catch (MissingResourceException mre) {
						}
					}
				} else {
					var param = mtchr.group(2);
					var sep = mtchr.group(3);
					if (sep.equals(":")) {
						var op = mtchr.group(4);
						var word = mtchr.group(5);

						if (op.equals("?")) {
							/*
							 * <code>${parameter:?word:otherWord}</code>
							 * 
							 * If parameter is unset or null, the expansion of otherWord is substituted.
							 * Otherwise, the expansion of word is substituted.
							 */
							var innerMtchr = ternPattern.matcher(word);
							if (innerMtchr.matches()) {
								if (conditionEvaluator.apply(param)) {
									return process(innerMtchr.group(1));
								} else {
									return process(innerMtchr.group(3));
								}
							} else {
								logger.ifPresent(l -> l.warning(
										"Invalid variable syntax ''{0}'' in variable expression. Expected true value and false value separated by '':'', not ''{1}''",
										input, word));
							}
						} else if (op.equals("-")) {
							/*
							 * <code>${parameter:-word}</code>
							 * 
							 * If parameter is unset or null, the expansion of word is substituted.
							 * Otherwise, the value of parameter is substituted.
							 */
							if (conditionEvaluator.apply(param)) {
								return expandVal(supplyVal(param));
							} else {
								return process(word);
							}
						} else if (op.equals("+")) {
							/*
							 * <code>${parameter:+word}</code>
							 * 
							 * If parameter is null or unset, nothing is substituted, otherwise the
							 * expansion of word is substituted.
							 */
							if (conditionEvaluator.apply(param)) {
								return process(word);
							} else {
								return "";
							}
						} else if (op.equals("=")) {
							/*
							 * <code>${parameter:=word}</code>
							 * 
							 * If parameter is null or unset, the expansion of word is substituted,
							 * otherwise nothing is substituted.
							 */
							if (conditionEvaluator.apply(param)) {
								return "";
							} else {
								return process(word);
							}
						} else {
							logger.ifPresent(l -> l.warning(
									"Invalid variable syntax ''{0}'' in variable expression. Unexpected option character ''{1}''",
									input, op));
						}
					} else {
						return expandVal(supplyVal(param));
					}
				}
			} else {
				logger.ifPresent(l -> l.warning("Invalid variable syntax ''{0}'' in variable expression.'", input));
			}

			return input;
		}

		private Object supplyVal(String param) {
			var supplier = variableSupplier.apply(param);
			if (missingThrowsException && supplier == null)
				throw new IllegalArgumentException(
						MessageFormat.format("Required variable ''{0}'' is missing", param));
			var val = supplier == null ? null : supplier.get();
			return val;
		}

		private String expandVal(Object var) {
			if (var instanceof Optional) {
				var opt = (Optional<?>) var;
				if (opt.isEmpty())
					var = null;
				else
					var = opt.get();
			}
			if (nullsAreEmpty && var == null)
				return "";
			else if (var == null)
				return null;
			else
				return String.valueOf(var);
		}
	}

	public interface Logger {
		void warning(String message, Object... args);

		void debug(String message, Object... args);
	}

	private final static class LazyDefaultStdOutLogger {
		final static Logger DEFAULT = new Logger() {

			@Override
			public void warning(String message, Object... args) {
				System.out.println("[WARNING]" + MessageFormat.format(message, args));
			}

			@Override
			public void debug(String message, Object... args) {
				System.out.println("[DEBUG] " + MessageFormat.format(message, args));
			}
		};
	}

	public final static Boolean evalAsCondition(Object val) {
		if (val instanceof Optional) {
			val = ((Optional<?>) val).orElse(null);
		}
		if (val == null || val.equals("") || val.equals(Boolean.FALSE) || val.equals(0)) {
			return false;
		} else {
			return true;
		}
	}

	public final static Logger defaultStdOutLogger() {
		return LazyDefaultStdOutLogger.DEFAULT;
	}

	public static class TemplateModel implements Closeable {

		public static TemplateModel ofContent(String content) {
			return new TemplateModel(new StringReader(content));
		}

		public static TemplateModel ofResource(String resource) {
			return ofResource(resource, Optional.empty());
		}

		public static TemplateModel ofResource(String resource, ClassLoader loader) {
			return ofResource(resource, Optional.of(loader));
		}

		public static TemplateModel ofResource(Class<?> packageClass, String childResource) {
			return ofResource(packageClass.getPackage().getName().replace('.', '/') + "/" + childResource,
					Optional.of(packageClass.getClassLoader()));
		}

		public static TemplateModel ofResource(String resource, Optional<ClassLoader> loader) {
			// Read resource content
			var ldr = loader.orElseGet(() -> Templates.class.getClassLoader());
			try {
				var res = ldr.getResource(resource);
				if (res == null) {

					throw new FileNotFoundException(resource);
				}
				var in = res.openStream();
				return new TemplateModel(new InputStreamReader(in));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

		}

		final Reader text;
		final Map<String, Supplier<Boolean>> conditions = new HashMap<>();
		final Map<String, Supplier<?>> variables = new HashMap<>();
		final Map<String, Function<String, List<TemplateModel>>> lists = new HashMap<>();
		final Map<String, Supplier<TemplateModel>> includes = new HashMap<>();
		final List<Function<Locale, ResourceBundle>> bundles = new ArrayList<>();
		private Optional<Supplier<Locale>> locale = Optional.empty();

		public final static Object[] NO_ARGS = new Object[0];

		private TemplateModel(Reader text) {
			this.text = text;
		}

		@Override
		public void close() {
			try {
				text.close();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		public Locale locale() {
			return locale.map(l -> l.get()).orElseGet(() -> Locale.getDefault());
		}

		public TemplateModel locale(Locale locale) {
			return locale(() -> locale);
		}

		public TemplateModel locale(Supplier<Locale> locale) {
			this.locale = Optional.of(locale);
			return this;
		}

		public List<ResourceBundle> bundles(Locale local) {
			return bundles.stream().map(b -> b.apply(local)).collect(Collectors.toList());
		}

		public List<ResourceBundle> bundles() {
			return bundles.stream().map(b -> b.apply(locale())).collect(Collectors.toList());
		}

		public TemplateModel bundles(Collection<ResourceBundle> bundles) {
			bundles.forEach(this::bundle);
			return this;
		}

		public TemplateModel bundles(ResourceBundle... bundles) {
			return bundles(Arrays.asList(bundles));
		}

		public TemplateModel bundle(ResourceBundle bundle) {
			return bundle(l -> bundle);
		}

		public TemplateModel bundle(Function<Locale, ResourceBundle> bundle) {
			bundles.add(bundle);
			return this;
		}

		public TemplateModel bundle(Class<?> clazz) {
			return bundle(l -> ResourceBundle.getBundle(clazz.getName(), l, clazz.getClassLoader()));
		}

		public boolean condition(String key) {
			var cond = conditions.get(key);
			if (cond == null)
				throw new IllegalArgumentException(MessageFormat.format("No condition with key {0}", key));
			return cond.get();
		}

		public TemplateModel condition(String key, Supplier<Boolean> condition) {
			conditions.put(key, condition);
			return this;
		}

		public TemplateModel condition(String key, boolean condition) {
			return condition(key, () -> condition);
		}

		public TemplateModel include(String key) {
			var inc = includes.get(key);
			if (inc == null)
				throw new IllegalArgumentException(MessageFormat.format("No include with key {0}", key));
			return inc.get();
		}

		public TemplateModel include(String key, TemplateModel model) {
			return include(key, () -> model);
		}

		public TemplateModel include(String key, Supplier<TemplateModel> model) {
			includes.put(key, model);
			return this;
		}

		public TemplateModel list(String key, List<TemplateModel> list) {
			return list(key, (content) -> list);
		}

		public TemplateModel list(String key, Function<String, List<TemplateModel>> list) {
			lists.put(key, list);
			return this;
		}

		public List<TemplateModel> list(String key, String content) {
			var l = lists.get(key);
			if (l == null)
				throw new IllegalArgumentException(MessageFormat.format("No list with key {0}", key));
			return l.apply(content);
		}

		@SuppressWarnings("unchecked")
		public <V> V variable(String key) {
			var val = variables.get(key);
			if (val == null)
				throw new IllegalArgumentException(MessageFormat.format("No variable with key {0}", key));
			return (V) val.get();
		}

		public TemplateModel variable(String key, Object variable) {
			return variable(key, () -> variable);
		}

		public TemplateModel variable(String key, Supplier<?> variable) {
			variables.put(key, variable);
			return this;
		}

		public TemplateModel i18n(String key, String i18n, Object... args) {
			return i18n(key, () -> i18n, () -> args);
		}

		public TemplateModel i18n(String key, Supplier<Object> variable) {
			return i18n(key, variable, () -> NO_ARGS);
		}

		public TemplateModel i18n(String key, Supplier<Object> variable, Supplier<Object[]> args) {
			return variable(key, () -> {
				var v = variable.get();
				if (v != null) {
					var a = args.get();
					for (var bundle : bundles) {
						try {
							if (a.length == 0)
								return bundle.apply(Locale.getDefault()).getString(String.valueOf(v));
							else
								return MessageFormat
										.format(bundle.apply(Locale.getDefault()).getString(String.valueOf(v)), a);
						} catch (MissingResourceException mre) {
						}
					}
				}
				return null;
			});
		}

		public TemplateModel withContent(String text) {
			var templ = new TemplateModel(new StringReader(text));
			templ.locale = locale;
			templ.bundles.addAll(bundles);
			templ.conditions.putAll(conditions);
			templ.variables.putAll(variables);
			templ.lists.putAll(lists);
			templ.includes.putAll(includes);
			return templ;
		}

		Locale calcLocale(TemplateModel model) {
			return model.locale.map(ol -> ol.get()).orElse(Locale.getDefault());
		}
	}

	/**
	 * Processes a block of text, replacing variable patterns
	 * and handling the XML-like processing directives such as <code>t:include</code>,
	 * <code>t:if</code> and <code>t:list</code>.
	 * <p>
	 * Variable values, conditions, lists and nested templates are all
	 * provided via the {@link TemplateModel}.
	 */
	public final static class TemplateProcessor {
	
		public enum State {
			START, TAG_START, T_TAG_START, T_TAG_NAME, T_TAG_LEAF_END, T_TAG_START_END, T_TAG_END, VAR_START, VAR_BRACE, TAG_END
		}
	
		public final static class Builder {
			private boolean nullsAreEmpty = true;
			private boolean missingThrowsException = true;
			private Optional<Logger> logger = Optional.empty();
			private Optional<VariableExpander> expander = Optional.empty();
	
			public Builder withNullsAsNull() {
				return withNullsAreEmpty(false);
			}
	
			public Builder withNullsAreEmpty(boolean nullsAreEmpty) {
				this.nullsAreEmpty = nullsAreEmpty;
				return this;
			}
	
			public Builder withMissingAsNull() {
				return withMissingThrowsException(false);
			}
	
			public Builder withMissingThrowsException(boolean missingThrowsException) {
				this.missingThrowsException = missingThrowsException;
				return this;
			}
	
			public Builder withLogger(Logger logger) {
				return withLogger(Optional.of(logger));
			}
	
			public Builder withLogger(Optional<Logger> logger) {
				this.logger = logger;
				return this;
			}
	
			public Builder withExpander(VariableExpander expander) {
				this.expander = Optional.of(expander);
				return this;
			}
	
			public TemplateProcessor build() {
				return new TemplateProcessor(this);
			}
	
		}
	
		private final boolean nullsAreEmpty;
		private final boolean missingThrowsException;
		private final Optional<Logger> logger;
		private final Optional<VariableExpander> expander;
		
		private final static class Condition {
			private final boolean negate;
			private final String name;
			
			
			private Condition(boolean negate, String name) {
				super();
				this.negate = negate;
				this.name = name;
			}
	
			private static Condition parse(String cond) {
				if(cond.startsWith("!"))
					return new Condition(true, cond.substring(1));
				else
					return new Condition(false, cond);
			}
		}
		
		private final static class Block {
			final StringBuilder out = new StringBuilder();
			final TemplateModel model;
			final VariableExpander expander;
			final Reader reader;
			final String scope;
			
			State state = State.START;
			boolean match = true;
			boolean capture = false;
			boolean inElse = false;
			int ifDepth = 0;
			
			Block(TemplateModel model, VariableExpander expander, Reader reader/* , String scope */) {
				this(model, expander, reader, null, true);
			}
			
			Block(TemplateModel model, VariableExpander expander, Reader reader, String scope, boolean match) {
				this.model = model;
				this.match = match;
				this.expander = expander;
				this.reader = reader;
				this.scope = scope;
			}
		}
	
		private TemplateProcessor(Builder bldr) {
			this.nullsAreEmpty = bldr.nullsAreEmpty;
			this.missingThrowsException = bldr.missingThrowsException;
			this.logger = bldr.logger;
			this.expander = bldr.expander;
		}
	
		public String process(TemplateModel model) {
			var block = new Block(model, getExpanderForModel(model), model.text);
			read(block);
			
			return block.out.toString();
		}
	
		private VariableExpander getExpanderForModel(TemplateModel model) {
			var locale = model.calcLocale(model);
	
			var exp = expander.orElseGet(() -> {
				return new VariableExpander.Builder().withMissingThrowsException(missingThrowsException)
						.withNullsAreEmpty(nullsAreEmpty).withLogger(logger)
						.withBundleSuppliers(model.bundles.stream().map(f -> {
							return new Supplier<ResourceBundle>() {
								@Override
								public ResourceBundle get() {
									return f.apply(locale);
								}
							};
						}).collect(Collectors.toList())).withVariableSupplier(model.variables::get)
						.withConditionEvaluator(cond -> conditionOrVariable(model, cond, "").orElseGet(() -> {
							logger.ifPresent(l -> l.debug("Missing condition {0}, assuming {1}", cond, false));
							return false;
						})).build();
			});
			return exp;
		}
	
		private void read(Block block) {
			int r;
			char ch;
			
			var buf = new StringBuilder();
			var esc = false;
	
			try {
				while ((r = block.reader.read()) != -1) {
					ch = (char) r;
	
					if (ch == '\\' && !esc) {
						esc = true;
						continue;
					}
					
					var process = !block.capture && ((block.match && !block.inElse) || (block.match && block.inElse));
	
					switch (block.state) {
					case START:
						if (ch == '$' && process) {
							block.state = State.VAR_START;
							buf.append(ch);
						} else if (ch == '<') {
							block.state = State.TAG_START;
							buf.append(ch);
						} else {
							flushBuf(ch, buf, block);
						}
						break;
					//
					// Vars
					//
					case VAR_START:
						if (ch == '{') {
							block.state = State.VAR_BRACE;
							buf.append(ch);
						} else {
							flushBuf(ch, buf, block);
						}
						break;
					case VAR_BRACE:
						// TODO nested variables
						if (!esc && ch == '}') {
							buf.append(ch);
							block.out.append(block.expander.process(buf.toString()));
							buf.setLength(0);
							block.state = State.START;
						} else {
							buf.append(ch);
						}
						break;
					//
					// Tags
					//
					case TAG_START:
						if (ch == '/') {
							block.state = State.TAG_END;
							buf.append(ch);
						}
						else if (ch == 't') {
							block.state = State.T_TAG_START;
							buf.append(ch);
						} else {
							flushBuf(ch, buf, block);
							block.state = State.START;
						}
						break;
					case T_TAG_START:
						if (ch == ':') {
							block.state = State.T_TAG_NAME;
							buf.append(ch);
						} else {
							flushBuf(ch, buf, block);
							block.state = State.START;
						}
						break;
					case T_TAG_NAME:
						if (ch == '>') {
							var directive = buf.toString().substring(1).trim();
							if(directive.startsWith("t:if ")) {
								block.ifDepth++;
							}
							if(process) {
								if(processDirective(block, directive)) {
									buf.setLength(0);
								}
								else {
									flushBuf(ch, buf, block);
									block.state = State.START;
								}
							}
							else {
								flushBuf(ch, buf, block);
								block.state = State.START;
							}
						} else if (ch == '/') {
							block.state = State.T_TAG_LEAF_END;
							buf.append(ch);
						} else {
							buf.append(ch);
						}
						break;
					case TAG_END:
						if (ch == 't') {
							block.state = State.T_TAG_START_END;
							buf.append(ch);
						} else {
							flushBuf(ch, buf, block);
							block.state = State.START;
						}
						break;
					case T_TAG_START_END:
						if (ch == ':') {
							block.state = State.T_TAG_END;
							buf.append(ch);
						} else {
							flushBuf(ch, buf, block);
							block.state = State.START;
						}
						break;
					case T_TAG_END:
						if(ch == '>') {
							var directive = buf.toString().substring(4).trim();
							var isIf = directive.equals("if");
							if(isIf) {
								block.ifDepth--;
							}
							if(directive.equals(block.scope) && (!isIf || (isIf && block.ifDepth == 0))) {
								logger.ifPresent(lg -> lg.debug("Leaving scope {0}", block.scope));
								return;
							} else {
								if(directive.equals(block.scope) && isIf) {
									buf.setLength(0);
								}
								else {
									flushBuf(ch, buf, block);
								}
								block.state = State.START;
							}
						}
						else {
							buf.append(ch);
						}
						break;
					case T_TAG_LEAF_END:
						if (ch == '>') {
							if(process || (block.scope.endsWith("if") && !block.inElse && !block.match)) {
								var directive = buf.toString().substring(1, buf.length() - 1);
								while(directive.endsWith("/"))
									directive = directive.substring(0, directive.length() - 1);
								directive = directive.trim();
								if(processDirective(block, directive)) {
									buf.setLength(0);
								}
								else {
									flushBuf(ch, buf, block);
									block.state = State.START;
								}
							}
							else {
								flushBuf(ch, buf, block);
								block.state = State.START;
							}
	
						} else {
							buf.append(ch);
						}
						break;
					}
	
					esc = false;
	
				}
			} catch (IOException ioe) {
				throw new UncheckedIOException(ioe);
			}
		}
		
		private boolean processDirective(Block block, String directive) {
			var spc = directive.indexOf(' ');
			var dir = ( spc == -1 ? directive : directive.substring(0, spc)).trim();
			var var = ( spc == -1 ? null : directive.substring(spc + 1).trim());
			if(dir.equals("t:if")) {
				
				var condition = Condition.parse(var);
				var content = "";
				var conditionOr = conditionOrVariable(block.model, condition.name, content);
				var match = false;
				
				if (conditionOr.isEmpty()) {
					logger.ifPresent(l -> l.debug("Missing condition {0}, assuming {1}", condition.name, false));
				} else {
					match = conditionOr.get();
				}
				
				if(condition.negate)
					match = !match;
				
				var ifBlock = new Block(block.model, getExpanderForModel(block.model), block.model.text, "if", match);
				ifBlock.ifDepth = 1;
				
				read(ifBlock);
				
				block.out.append(ifBlock.out.toString());
				ifBlock.out.setLength(0);
				block.state = State.START;
				
				return true;
			}
			else if(dir.equals("t:include")) {
				var includeModel = block.model.includes.get(var);
				if (includeModel == null) {
					logger.ifPresent(l -> l.warning("No include in model named {0}", var));
					return false;
				} else {
					var include = includeModel.get();
					var incBlock = new Block(include, getExpanderForModel(include), include.text);
					read(incBlock);
					block.out.append(incBlock.out.toString());
					block.state = State.START;
					return true;
				}
			}
			else if(dir.equals("t:else")) {
				block.match = !block.match;
				block.inElse = true;
				block.state = State.START;
				return true;
			}
			else if(dir.equals("t:list")) {
				var listSupplier = block.model.lists.get(var);
				if (listSupplier == null) {
					logger.ifPresent(l -> l.warning("Missing list {0} in message template", var));
					return false;
				}
				else {
					/* Temporary block to read all the content we must repeat */
					var tempBlock = new Block(block.model, block.expander, block.reader, "list", true);
					tempBlock.capture = true;
					read(tempBlock);
					
					for(var templ : listSupplier.apply(tempBlock.out.toString())) {
						var listBlock = new Block(templ, getExpanderForModel(templ), templ.text);
						read(listBlock);									
						block.out.append(listBlock.out.toString());
					}
					
					block.state = State.START;
					return true;
				}
			}
			else {
				logger.ifPresent(l -> l.warning("Unknown 't' tag, {0}", dir));
				return false;
			}
		}
	
		@SuppressWarnings("rawtypes")
		private static Optional<Boolean> conditionOrVariable(TemplateModel model, String attributeName, String content) {
			if (model.conditions.containsKey(attributeName)) {
				return Optional.of(model.conditions.get(attributeName).get());
			} else if (model.includes.containsKey(attributeName)) {
				return Optional.of(true);
			} else if (content != null && model.lists.containsKey(attributeName)) {
				return Optional.of(!(model.lists.get(attributeName).apply(content).isEmpty()));
			} else if (model.variables.containsKey(attributeName)) {
				var var = model.variables.get(attributeName).get();
	
				if (var instanceof Optional) {
					var = ((Optional) var).isEmpty() ? null : ((Optional) var).get();
				}
	
				if (var instanceof Boolean) {
					return Optional.of((Boolean) var);
				} else if (var instanceof String) {
					return Optional.of(!"false".equalsIgnoreCase((String) var) && !"".equals((String) var) && !"0".equals((String) var));
				} else if (var instanceof Number) {
					return Optional.of(((Number) var).doubleValue() > 0);
				} else if (var != null) {
					return Optional.of(Boolean.TRUE);
				}
			}
			return Optional.empty();
		}
	
		private void flushBuf(char ch, StringBuilder buf, Block block) {
			if (buf.length() > 0) {
				if(block.match) {
					buf.append(ch);
					block.out.append(buf.toString());
				}
				buf.setLength(0);
			} else {
				if(block.match) {
					block.out.append(ch);
				}
			}
		}
	}

}
