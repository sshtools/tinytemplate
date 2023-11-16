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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.sshtools.tinytemplate.Templates.Logger;
import com.sshtools.tinytemplate.Templates.TemplateModel;
import com.sshtools.tinytemplate.Templates.TemplateProcessor;
import com.sshtools.tinytemplate.Templates.VariableExpander;

public class TemplatesTest {
	
	@Test
	public void testExpandSimple() {
		var exp = new VariableExpander.Builder().
				withMissingThrowsException(false).
				fromSimpleMap(createVars()).
				build();
		assertEquals("Some name", exp.expand("NAME"));
		assertEquals("Some description", exp.expand("DESCRIPTION"));
		assertEquals("27", exp.expand("AGE"));
		assertEquals("15.323452", exp.expand("FRAC"));
		assertEquals("true", exp.expand("HERE"));
		assertEquals("true", exp.expand("OPT_HERE"));
		assertEquals("", exp.expand("OPT_NOT_HERE"));
		assertEquals("", exp.expand("__MISSING__"));
	}
	
	@Test
	public void testExpandSubstIfFalseOrVariable() {
		
		var exp = new VariableExpander.Builder().
				withMissingAsNull().
				fromSimpleMap(createVars()).
				build();
		
		assertEquals("it's missing", exp.expand("${__MISSING__:-it's missing"));
		assertEquals("Some name", exp.expand("${NAME:-it's missing"));
	}
	
	@Test
	public void testExpandSubstIfFalseOrNothing() {
		
		var exp = new VariableExpander.Builder().
				withMissingAsNull().
				fromSimpleMap(createVars()).
				build();
		
		assertEquals("", exp.expand("${__MISSING__:+it's missing"));
		assertEquals("it's missing", exp.expand("${NAME:+it's missing"));
	}
	
	@Test
	public void testExpandSubstIfTrue() {
		
		var exp = new VariableExpander.Builder().
				withMissingAsNull().
				fromSimpleMap(createVars()).
				build();
		
		assertEquals("it's missing", exp.expand("${__MISSING__:=it's missing"));
		assertEquals("", exp.expand("${NAME:=it's missing"));
	}
	
	@Test
	public void testExpandMissingNullAsNull() {
		var exp = new VariableExpander.Builder().
				withNullsAsNull().
				withMissingAsNull().
				fromSimpleMap(createVars()).
				build();
		
		assertEquals(null, exp.expand("__MISSING__"));
	}
	
	@Test
	public void testExpandMissingNullAsBlank() {
		var exp = new VariableExpander.Builder().
				withMissingThrowsException(false).
				fromSimpleMap(createVars()).
				build();
		
		assertEquals("", exp.expand("__MISSING__"));
	}
	
	@Test
	public void testExpandLogger() {
		var out = new ArrayList<String>();
		var exp = new VariableExpander.Builder().
				withLogger(new Logger() {
					
					@Override
					public void warning(String message, Object... args) {
						out.add("WARN: " + MessageFormat.format(message, args));
					}
					
					@Override
					public void debug(String message, Object... args) {
						out.add("DEBUG: " + MessageFormat.format(message, args));
					}
				}).
				fromSimpleMap(createVars()).
				build();
		
		exp.expand("Z:/X");
		exp.expand("NAME:?SOMETHING");
		exp.expand("NAME:_SOMETHING");
		
		
		assertEquals("DEBUG: Expanding 'Z:/X'", out.get(0));
		assertEquals("WARN: Invalid variable syntax 'Z:/X' in variable expression.", out.get(1));
		assertEquals("DEBUG: Expanding 'NAME:?SOMETHING'", out.get(2));
		assertEquals("WARN: Invalid variable syntax 'NAME:?SOMETHING' in variable expression. Expected true value and false value separated by ':', not 'SOMETHING'", out.get(3));
		assertEquals("DEBUG: Expanding 'NAME:_SOMETHING'", out.get(4));
		assertEquals("WARN: Invalid variable syntax 'NAME:_SOMETHING' in variable expression. Unexpected option character ''", out.get(5));
	}
	
	@Test
	public void testExpandMissingThrows() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			var exp = new VariableExpander.Builder().
					fromSimpleMap(createVars()).
					build();
			
			exp.expand("__MISSING__");	
		});
		
	}
	
	@Test
	public void testExpandI18N() {
		var exp = new VariableExpander.Builder().
				withBundles(ResourceBundle.getBundle(TemplatesTest.class.getName())).
				build();
		
		assertEquals("Some localised key", exp.expand("%someKey"));
		assertEquals("%someMissingKey", exp.expand("%someMissingKey"));
	}
	
	@Test
	public void testExpandTernary() {
		
		var exp = new VariableExpander.Builder().
				fromSimpleMap(createVars()).
				withMissingThrowsException(false).
				build();
		
		assertEquals("left", exp.expand("HERE:?left:right"));
		assertEquals("right", exp.expand("NOT_HERE:?left:right"));
		assertEquals("left", exp.expand("NAME:?left:right"));
		assertEquals("right", exp.expand("MISSING:?left:right"));
		assertEquals("left", exp.expand("OPT_HERE:?left:right"));
		assertEquals("right", exp.expand("OPT_NOT_HERE:?left:right"));
		assertEquals("", exp.expand("NOT_HERE:?left:"));
	}
	
	@Test
	public void testExpandNested() {
		
		var exp = new VariableExpander.Builder().
				fromSimpleMap(createVars()).
				withMissingAsNull().
				withNullsAsNull().
				build();
		
		assertEquals("Some name", exp.expand("HERE:?${NAME}:${DESCRIPTION}"));
		assertEquals("Some description", exp.expand("NOT_HERE:?${NAME}:${DESCRIPTION}"));
		assertEquals("null", exp.expand("NOT_HERE:-${__MISSING__}"));
	}
	
		@Test
	public void testTemplateSimpleVariables() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>Variable var: Some name. And var2: <i>Some description</i></p>
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p>Variable var: ${NAME}. And var2: <i>${DESCRIPTION}</i></p> 
				</body>
				</html>
				 	 """).
					variable("NAME", "Some name").
					variable("DESCRIPTION", "Some description")));
		
	}
	
	
	@Test
	public void testUnknownLeafTTag() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p><t:xxxxxx></p>
				<p><t:xxxxxx/></p>
				<p><t:xxxxxx block1/></p>
				<p><t:xxxxxx block2 /></p>
				<p><t:xxxxxx block3 / ></p>
				<p><t:xxxxxx block4 / ></p>
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p><t:xxxxxx></p>
				<p><t:xxxxxx/></p>
				<p><t:xxxxxx block1/></p>
				<p><t:xxxxxx block2 /></p>
				<p><t:xxxxxx block3 / ></p>
				<p><t:xxxxxx block4 / ></p>
				</body>
				</html>
				 	 """)));
	}
		
	
	@Test
	public void testTemplateInclude() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>Some block 1</p>
				<p>Some block 2</p>
				<p>Some block 3</p>
				<p>Some block 4</p>
				</body>
				</html>Some block 5
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p><t:include block1/></p>
				<p><t:include block2 /></p>
				<p><t:include block3 / ></p>
				<p><t:include block4 / ></p>
				</body>
				</html><t:include block5 / >
				 	 """).
				include("block1", TemplateModel.ofContent("Some block 1")).
				include("block2", TemplateModel.ofContent("Some block 2")).
				include("block3", TemplateModel.ofContent("Some block 3")).
				include("block4", TemplateModel.ofContent("Some block 4")).
				include("block5", TemplateModel.ofContent("Some block 5"))));
		
	}
	
	@Test
	public void testTemplateIf() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Show this</p>
				
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Show this</p>
				</t:if>
				<t:if bCondition>
				<p>Don't show this</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", true)));
	}
	
	@Test
	public void testTemplateVariableAsCondition() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Show this Some value</p>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aVariable>
				<p>Show this ${aVariable}</p>
				</t:if>
				</body>
				</html>
				 	 """).
					variable("aVariable", "Some value")));
	}
	
	@Test
	public void testTemplateIfCheckNotProcessing() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Show this ${missingVar}</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", false)));
	}
	
	@Test
	public void testTemplateIfWithElse() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Show this</p>
				
				<hr/>
				
				<p>And show this</p>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>				
				<t:if aCondition>
				<p>Show this</p>
				<t:else/>
				<p>Don't show this</p>
				</t:if>
				<hr/>
				<t:if bCondition>
				<p>Don't show this</p>
				<t:else/>
				<p>And show this</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", true)));
	}
	
	@Test
	public void testTemplateWithList() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<div>Row 1</div>
				
				<div>Row 2</div>
				
				<div>Row 3</div>
				
				<div>Row 4</div>
				
				<div>Row 5</div>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:list aList>
				<div>${row}</div>
				</t:list>
				</body>
				</html>
				 	 """).
					list("aList", (content) -> IntStream.of(1,2,3,4,5).mapToObj(i -> {
						return TemplateModel.ofContent(content).
							variable("row", "Row " + i);
					}).toList()).
					variable("var1", "Some Name")));
	}
	
	@Test
	public void testTemplateNestedList() {
		Assertions.assertEquals("""
				<html>
				<body>
				
						<div>Row 1</div>
						<div>more text</div>
				\t\t
							<div>some inner text</div>
							<div>A</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>B</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>C</div>
							<div>more inner text</div>
				\t\t
						<div>yet more text</div>
						<div>and yet more text</div>
				
						<div>Row 2</div>
						<div>more text</div>
				\t\t
							<div>some inner text</div>
							<div>A</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>B</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>C</div>
							<div>more inner text</div>
				\t\t
						<div>yet more text</div>
						<div>and yet more text</div>
				
						<div>Row 3</div>
						<div>more text</div>
				\t\t
							<div>some inner text</div>
							<div>A</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>B</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>C</div>
							<div>more inner text</div>
				\t\t
						<div>yet more text</div>
						<div>and yet more text</div>
				
						<div>Row 4</div>
						<div>more text</div>
				\t\t
							<div>some inner text</div>
							<div>A</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>B</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>C</div>
							<div>more inner text</div>
				\t\t
						<div>yet more text</div>
						<div>and yet more text</div>
				
						<div>Row 5</div>
						<div>more text</div>
				\t\t
							<div>some inner text</div>
							<div>A</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>B</div>
							<div>more inner text</div>
				\t\t
							<div>some inner text</div>
							<div>C</div>
							<div>more inner text</div>
				\t\t
						<div>yet more text</div>
						<div>and yet more text</div>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:list aList>
						<div>${row}</div>
						<div>more text</div>
						<t:list bList>
							<div>some inner text</div>
							<div>${innerRow}</div>
							<div>more inner text</div>
						</t:list>
						<div>yet more text</div>
						<div>and yet more text</div>
				</t:list>
				</body>
				</html>
				 	 """).
					list("aList", (content) -> IntStream.of(1,2,3,4,5).mapToObj(i -> {
						return TemplateModel.ofContent(content).
							variable("row", "Row " + i).
							list("bList", (bcontent) -> Stream.of("A", "B", "C").map(a -> {
								return TemplateModel.ofContent(bcontent).
										variable("innerRow", a);
							}).toList());
					}).toList()).
					variable("var1", "Some Name")));
	}
	
	@Test
	public void testTemplateWithConditionsInList() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<div>Row 1 Whatever</div>
				
				
				<div>Row 2 Row2</div>
				
				
				<div>Row 3 Whatever</div>
				
				<p>This is row 3</p>
				
				
				<div>Row 4 Whatever</div>
				
				
				<div>Row 5 Whatever</div>
				
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:list aList>
				<div>${row} ${thisIsRow2:?Row2:Whatever}</div>
				<t:if thisIsRow3>
				<p>This is row 3</p>
				</t:if>
				</t:list>
				</body>
				</html>
				 	 """).
					list("aList", (content) -> IntStream.of(1,2,3,4,5).mapToObj(i -> {
						return TemplateModel.ofContent(content).
							variable("row", "Row " + i).
							condition("thisIsRow3", i == 3).
							condition("thisIsRow2", i == 2);
					}).toList()).
					variable("var1", "Some Name")));
	}
	
	public void testTemplateIfWithVariables() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>Same value in outer: Some Name</p>
				
				<p>Show this: Some Name</p>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p>Same value in outer: ${var1}</p>
				<t:if aCondition>
				<p>Show this: ${var1}</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", true).
					variable("var1", "Some Name")));
	}
	
	
	@Test
	public void testTemplateIncludeWithList() {
		Assertions.assertEquals("""
				<html>
				<body>
				
					
					<div class="alert alert-style-0" role="alert">
						
						Title 0
						
					</div>
				
					<div class="alert alert-style-1" role="alert">
						
						Title 1
						
					</div>
				
					<div class="alert alert-style-2" role="alert">
						
						Title 2
						
					</div>
				
					<div class="alert alert-style-3" role="alert">
						
							<i class="bi bi-icon-3"></i>
						
						Title 3
						
					</div>
				
					<div class="alert alert-style-4" role="alert">
						
							<i class="bi bi-icon-4"></i>
						
						Title 4
						
							<br/>
							<small>Description4</small>
						
					</div>
				
				
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent(
				"""
				<html>
				<body>
				<t:if alerts>
					<t:include alerts />
				</t:if>
				</body>
				</html>
			 	""").
				include("alerts", 
					TemplateModel.ofContent("""
						<t:list items>
							<div class="alert alert-${style}" role="alert">
								<t:if icon>
									<i class="bi bi-${icon}"></i>
								</t:if>
								${title}
								<t:if description>
									<br/>
									<small>${description}</small>
								</t:if>
							</div>
						</t:list>
						""").list("items", (content) -> {
							var l = new ArrayList<TemplateModel>();
							for(int i = 0 ; i < 5 ; i++) {
								var itemMdl = TemplateModel.ofContent(content).
										variable("style", "style-" + i).
										variable("title", "Title " + i);
								if(i > 2)
									itemMdl.variable("icon", "icon-" + i);
								if(i > 3)
									itemMdl.variable("description", "Description" + i);
								l.add(itemMdl);
							}
							return l;
						})
					)
				).stripIndent());
	}
	
	@Test
	public void testTemplateIfWithInclude() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Start ...</p>
				INCLUDED TEXT
				<p>End ...</p>
				
				MORE INCLUDED TEXT
				
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Start ...</p>
				<t:include block1/>
				<p>End ...</p>
				<t:if bCondition>
				<t:include block2/>
				</t:if>
				</t:if>
				</body>
				</html>
			 	 """).
				condition("aCondition", true).
				condition("bCondition", true).
				include("block1", 
						TemplateModel.ofContent("INCLUDED TEXT")).
				include("block2", 
						TemplateModel.ofContent("MORE INCLUDED TEXT"))
				));
	}
	
	@Test
	public void testTemplateNestedIf() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Show this</p>
				
				<p>And show this too</p>
				
				
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Show this</p>
				<t:if !bCondition>
				<p>And show this too</p>
				</t:if>
				</t:if>
				<t:if bCondition>
				<p>Don't show this</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", true)));
		
	}
	
	public void testTEMPYYYY() {
		String TODTEST = """
			
		<t:if ntp>
			<div class="float-end">
				<t:if ntpSynchronized>
					<i title="${%ntpSynchronized}" class="icn-small text-success bi-check-circle-fill me-2 align-middle"></i>
				<t:else/>
					<i title="${%ntpNotSynchronized}" class="icn-small text-warning bi-exclamation-triangle-fill me-2 align-middle"></i>
				</t:if>
			</div>
		</t:if>
		""";
		
	}
	
	@Test
	public void testTEMPXXXXXXX() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Show this</p>
				
				<p>And show this too</p>
				
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Show this</p>
				<t:if !bCondition>
				<p>And show this too</p>
				</t:if>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", true)));
		
	}
	
	@Test
	public void testTemplateNestedIf2() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Outside</p>
				<t:if bCondition>
				<p>And show this too</p>
				</t:if>
				<p>Outside</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", false).
					condition("bCondition", true)));
		
	}
	
	@Test
	public void testTemplateNestedIf3() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Outside</p>
				
				<p>Outside</p>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Outside</p>
				<t:if bCondition>
				<p>Don't show this</p>
				<t:if bcondition>
				<p>Or this</p>
				</t:if>
				</t:if>
				<p>Outside</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("aCondition", true).
					condition("bCondition", false).
					condition("cCondition", true)));
		
	}
	
	private TemplateProcessor createParser() {
		return new TemplateProcessor.Builder().build();
	}
	
	public static Map<String, ?> createVars() {
		var map = new HashMap<String, Object>();
		map.put("NAME", "Some name");
		map.put("DESCRIPTION", "Some description");
		map.put("AGE", 27);
		map.put("FRAC", 15.323452);
		map.put("HERE", true);
		map.put("GO_AWAY", false);
		map.put("NOT_HERE", false);
		map.put("OPT_NOT_HERE", Optional.empty());
		map.put("OPT_HERE", Optional.of(true));
		return map;
	}

	

}
