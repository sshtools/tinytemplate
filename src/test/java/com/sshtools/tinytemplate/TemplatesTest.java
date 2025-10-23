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
import java.util.Arrays;
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
		
		
		assertEquals("DEBUG: Expanding `Z:/X`", out.get(0));
		assertEquals("WARN: Invalid variable syntax `Z:/X` in variable expression.", out.get(1));
		assertEquals("DEBUG: Expanding `NAME:?SOMETHING`", out.get(2));
		assertEquals("WARN: Invalid variable syntax `NAME:?SOMETHING` in variable expression. Expected true value and false value separated by ':', not `SOMETHING`", out.get(3));
		assertEquals("DEBUG: Expanding `NAME:_SOMETHING`", out.get(4));
		assertEquals("WARN: Invalid variable syntax `NAME:_SOMETHING` in variable expression. Unexpected option character ``", out.get(5));
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
				fromSimpleMap(createVars()).
				build();
		
		assertEquals("Some localised key", exp.expand("%someKey"));
		assertEquals("%someMissingKey", exp.expand("%someMissingKey"));
		assertEquals("Some key with args arg0 arg1 arg2", exp.expand("%someKeyWithArgs arg0,arg1,arg2"));
		assertEquals("Some key with args 27 arg1 Some name", exp.expand("%someKeyWithArgs ${AGE},arg1,${NAME}"));
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
	public void testIgnoreScript() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>Variable var: Some name. And var2: <i>Some description</i></p>
				</body>
				
				<script>
				console.log("This ${NAME} won't get processed");
				</script>
				
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p>Variable var: ${NAME}. And var2: <i>${DESCRIPTION}</i></p> 
				</body>
				<t:ignore>
				<script>
				console.log("This ${NAME} won't get processed");
				</script>
				</t:ignore>
				</html>
				 	 """).
					variable("NAME", "Some name").
					variable("DESCRIPTION", "Some description")));
	}
	
	@Test
	public void testVarPatternInScript() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>Variable var: Some name. And var2: <i>Some description</i></p>
				</body>
				<script>
				var a = $("X");
				console.log("XXX");
				if(a > b) {
					console.log("Stuff");
				}
				var b = $("X");
				</script>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p>Variable var: ${NAME}. And var2: <i>${DESCRIPTION}</i></p> 
				</body>
				<script>
				var a = $("X");
				console.log("XXX");
				if(a > b) {
					console.log("Stuff");
				}
				var b = $("X");
				</script>
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
					condition("aCondition", true).
					condition("bCondition", false)));
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
	public void testTemplateParentVariableInsideIf() {
		Assertions.assertEquals("""
				<html>
				<body>
				<span>Some other value</span>
				
				<p>Show this Some value</p>
				<span>Some other value</span>
				
				</body>
				</html>
				""".trim(), 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<span>${someVar}</span>
				<t:if aVariable>
				<p>Show this ${aVariable}</p>
				<span>${someVar}</span>
				</t:if>
				</body>
				</html>
				 	 """).
					variable("someVar", "Some other value").
					variable("aVariable", "Some value")).trim());
	}
	
	@Test
	public void testTemplateParentVariableInsideList() {
		Assertions.assertEquals("""
				<html>
				<body>
				<span>Some other value</span>
				
				<span>Some other value</span>
				<span>Val 1</span>
				
				<span>Some other value</span>
				<span>Val 2</span>
				
				</body>
				</html>
				""".trim(), 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<span>${someVar}</span>
				<t:list aList>
				<span>${someVar}</span>
				<span>${someInnerVar}</span>
				</t:list>
				</body>
				</html>
				 	 """).
					variable("someVar", "Some other value").
					list("aList", (cnt) -> {
						return Arrays.asList(
								TemplateModel.ofContent(cnt).
									variable("someInnerVar", "Val 1"),
								TemplateModel.ofContent(cnt).
									variable("someInnerVar", "Val 2")
						);
					})).trim());
	}
	
	@Test
	public void testTemplateParentVariableInsideListInsideIf() {
		Assertions.assertEquals("""
				<html>
				<body>
				<span>Some other value</span>
				
				
				<span>Some other value</span>
				<span>Val 1</span>
				
				<span>Some other value</span>
				<span>Val 2</span>
				
				
				</body>
				</html>
				""".trim(), 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<span>${someVar}</span>
				<t:if aList>
				<t:list aList>
				<span>${someVar}</span>
				<span>${someInnerVar}</span>
				</t:list>
				</t:if>
				</body>
				</html>
				 	 """).
					variable("someVar", "Some other value").
					list("aList", (cnt) -> {
						return Arrays.asList(
								TemplateModel.ofContent(cnt).
									variable("someInnerVar", "Val 1"),
								TemplateModel.ofContent(cnt).
									variable("someInnerVar", "Val 2")
						);
					})).trim());
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
					condition("aCondition", true).
					condition("bCondition", false)));
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
	public void testTemplateIfAfterIfAndElseWithNestedIf() {
		Assertions.assertEquals("""
				<html>
				<body>
				
				<p>Show this</p>
				
				<p>And this</p>
				
				
				
				<p>Do show this</p>
				
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<t:if aCondition>
				<p>Show this</p>
				<t:if cCondition>
				<p>And this</p>
				</t:if>
				<t:else/>
				<p>Dont show this</p>
				</t:if>
				<t:if bCondition>
				<p>Do show this</p>
				</t:if>
				</body>
				</html>
				 	 """).
					condition("cCondition", true).
					condition("aCondition", true).
					condition("bCondition", true)));
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
					condition("aCondition", true).
					condition("bCondition", false)));
		
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
	public void testTemplateNestedIfs1() {
		Assertions.assertEquals(
				"<span>Result 1</span>", 
				createParser().process(TemplateModel.ofContent("""
					<t:if var1>
					    <t:if cond2>
							<t:if cond3>
								<span>Result 1</span>
							<t:else/>
								<span>Result 2</span>
							</t:if>
						<t:else/>
						    <t:if cond3>
								<span>Result 3</span>
					        <t:else/>
								<span>Result 4</span>
					        </t:if>
						</t:if>
					<t:else/>
					    <t:if cond3>
							<span>Result 4</span>
					    <t:else/>
							<span>Result 5</span>
					    </t:if>
					</t:if>
				 	 """).
					variable("var1", "Some var").
					condition("cond2", true).
					condition("cond3", true)).trim());
	}
	
	@Test
	public void testTemplateNestedIfs2() {
		Assertions.assertEquals(
				"<span>Result 2</span>", 
				createParser().process(TemplateModel.ofContent("""
					<t:if var1>
					    <t:if cond2>
							<t:if cond3>
								<span>Result 1</span>
							<t:else/>
								<span>Result 2</span>
							</t:if>
						<t:else/>
						    <t:if cond3>
								<span>Result 3</span>
					        <t:else/>
								<span>Result 4</span>
					        </t:if>
						</t:if>
					<t:else/>
					    <t:if cond3>
							<span>Result 4</span>
					    <t:else/>
							<span>Result 5</span>
					    </t:if>
					</t:if>
				 	 """).
					variable("var1", "Some var").
					condition("cond2", true).
					condition("cond3", false)).trim());
	}
	
	@Test
	public void testTemplateNestedIfs3() {
		Assertions.assertEquals(
				"<span>Result 3</span>", 
				createParser().process(TemplateModel.ofContent("""
					<t:if var1>
					    <t:if cond2>
							<t:if cond3>
								<span>Result 1</span>
							<t:else/>
								<span>Result 2</span>
							</t:if>
						<t:else/>
						    <t:if cond3>
								<span>Result 3</span>
					        <t:else/>
								<span>Result 4</span>
					        </t:if>
						</t:if>
					<t:else/>
					    <t:if cond3>
							<span>Result 5</span>
					    <t:else/>
							<span>Result 6</span>
					    </t:if>
					</t:if>
				 	 """).
					variable("var1", "Some var").
					condition("cond2", false).
					condition("cond3", true)).trim());
	}
	
	@Test
	public void testTemplateNestedIfs4() {
		Assertions.assertEquals(
				"<span>Result 4</span>", 
				createParser().process(TemplateModel.ofContent("""
					<t:if var1>
					    <t:if cond2>
							<t:if cond3>
								<span>Result 1</span>
							<t:else/>
								<span>Result 2</span>
							</t:if>
						<t:else/>
						    <t:if cond3>
								<span>Result 3</span>
					        <t:else/>
								<span>Result 4</span>
					        </t:if>
						</t:if>
					<t:else/>
					    <t:if cond3>
							<span>Result 5</span>
					    <t:else/>
							<span>Result 6</span>
					    </t:if>
					</t:if>
				 	 """).
					variable("var1", "Some var").
					condition("cond3", false).
					condition("cond2", false)).trim());
	}
	
	@Test
	public void testTemplateNestedIfs5() {
		Assertions.assertEquals(
				"<span>Result 5</span>", 
				createParser().process(TemplateModel.ofContent("""
					<t:if var1>
					    <t:if cond2>
							<t:if cond3>
								<span>Result 1</span>
							<t:else/>
								<span>Result 2</span>
							</t:if>
						<t:else/>
						    <t:if cond3>
								<span>Result 3</span>
					        <t:else/>
								<span>Result 4</span>
					        </t:if>
						</t:if>
					<t:else/>
					    <t:if cond3>
							<span>Result 5</span>
					    <t:else/>
							<span>Result 6</span>
					    </t:if>
					</t:if>
				 	 """).
					condition("cond3", true)).trim());
	}
	
	@Test
	public void testTemplateNestedIfs6() {
		Assertions.assertEquals(
				"<span>Result 6</span>", 
				createParser().process(TemplateModel.ofContent("""
					<t:if var1>
					    <t:if cond2>
							<t:if cond3>
								<span>Result 1</span>
							<t:else/>
								<span>Result 2</span>
							</t:if>
						<t:else/>
						    <t:if cond3>
								<span>Result 3</span>
					        <t:else/>
								<span>Result 4</span>
					        </t:if>
						</t:if>
					<t:else/>
					    <t:if cond3>
							<span>Result 5</span>
					    <t:else/>
							<span>Result 6</span>
					    </t:if>
					</t:if>
				 	 """)).trim());
	}
	
	
//	@Test
//	public void testTemplateThreeDeep() {
//		Assertions.assertEquals(
//				"<div class=\"form-floating input-group\">\n" +
//				"                <textarea>Some content with blahg</textarea>\n" +
//				"                <labelW for=\"12345\" class=\"label-class\">Some label</label>\n" +
//				"            </div>\n" +
//				"		\n" +
//				"	\n" +
//				"\n" +
//				"\n" +
//				"    <div id=\"zzzzzHelp\" class=\"form-text text-muted\">Some help</div>", 
//				createParser().process(TemplateModel.ofContent("""
//					<t:if label>
//					    <t:if label.floating>
//							<t:if input.group>
//					            <div class="form-floating input-group">
//					                <t:include input/>
//					                <labelW for="${input.id}" class="${label.class}">${label}</label>
//					            </div>
//							<t:else/>
//						        <div class="form-floating">
//						            <t:include input/>
//						            <labelR for="${input.id}" class="${label.class}">${label}</label>
//						        </div>
//							</t:if>
//						<t:else/>
//						    <t:if input.group>
//					            <div class="input-group">
//						            <t:if label.first>
//							            <labelX for="${input.id}" class="${label.class}">${label}</label>
//							        </t:if>
//							        <t:include input/>
//							        <t:if !label.first>
//							            <labelY for="${input.id}" class="${label.class}">${label}</label>
//							        </t:if>
//					            </div>
//					        <t:else/>
//						        <t:if label.first>
//						            <labelZ for="${input.id}" class="${label.class}">${label}</label>
//						        </t:if>
//						        <t:include input/>
//						        <t:if !label.first>
//						            <labelQ for="${input.id}" class="${label.class}">${label}</label>
//						        </t:if>
//					        </t:if>
//						</t:if>
//					<t:else/>
//					    <t:if input.group>
//					        <div class="input-group">
//					            <t:include input/>
//					        </div>
//					    <t:else/>
//					        <t:include input/>
//					    </t:if>
//					</t:if>
//					<t:if help>
//					    <div id="${id}Help" class="form-text text-muted">${help}</div>
//					</t:if>
//				 	 """).
//					variable("label", "Some label").
//					variable("help", "Some help").
//					variable("input.id", "12345").
//					include("input", TemplateModel.ofContent("<textarea>Some content with ${aVar}</textarea>").variable("aVar", "blahg")).
//					variable("id", "zzzzz").
//					condition("has.label", true).
//					condition("label.first", false).
//					condition("input.group", true).
//					condition("label.floating", true).
//					variable("label.class", "label-class")).trim());
//	}
	
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
				<t:if bCondition>
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
	
	@Test
	public void testTemplateObjectMissing() {
		Assertions.assertEquals("""
				<html>
				<body>
				<t:object aTemplate>
				<p>Should not be visible</p>
				<p>Name: </p>
				</t:object>
				</body>
				</html>
				""", 
				new TemplateProcessor.Builder().withMissingAsNull().build().process(
		TemplateModel.ofContent("""
				<html>
				<body>
				<t:object aTemplate>
				<p>Should not be visible</p>
				<p>Name: ${name}</p>
				</t:object>
				</body>
				</html>
				 	 """)));
		
	}
	
	@Test
	public void testTemplateIncludeMorThanOnce() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>First Time
				Some content
				Some content
				</body>
				</html>
				""", 
				createParser().process(
		TemplateModel.ofContent("""
				<html>
				<body>
				<p>First Time
				<t:include templ/>
				<t:include templ/>
				</body>
				</html>
				 	 """).
			include("templ", TemplateModel.ofContent("Some content"))));
		
	}
	
	@Test
	public void testTemplateObjectInCondition() {
		Assertions.assertEquals("""
				<p>Some text</p>


				<p>Name: Joe B</p>
				
				
				<p>Some other text</p>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<p>Some text</p>
				<t:if aPerson>
				<t:object aPerson>
				<p>Name: ${name}</p>
				</t:object>
				</t:if>
				<p>Some other text</p>
				 	 """).
				object("aPerson", (c) ->
					TemplateModel.ofContent(c).
						variable("name", "Joe B")
				)));
		
	}
	
	@Test
	public void testTemplateI18N() {
		Assertions.assertEquals("""
				<p>Some text</p>
				<p>Some localised key</p>
				<p>Some other text</p>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<p>Some text</p>
				<p>${%someKey}</p>
				<p>Some other text</p>
				 	 """).bundle(TemplatesTest.class)
				));
		
	}
	
	@Test
	public void testTemplateI18NArgs() {
		Assertions.assertEquals("""
				<p>Some text</p>
				<p>Some key with args arg0 arg1 arg2</p>
				<p>Some other text</p>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<p>Some text</p>
				<p>${%someKeyWithArgs arg0,arg1,arg2}</p>
				<p>Some other text</p>
				 	 """).bundle(TemplatesTest.class)
				));
	}
	
	@Test
	public void testTemplateI18NArgsAndEscapes() {
		Assertions.assertEquals("""
				<p>Some text</p>
				<p>Some key with args arg0,\\ arg1 arg2</p>
				<p>Some other text</p>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<p>Some text</p>
				<p>${%someKeyWithArgs arg0\\\\,\\\\\\\\,arg1,arg2}</p>
				<p>Some other text</p>
				 	 """).bundle(TemplatesTest.class)
				));
	}
	
	@Test
	public void testTemplateI18NVars() {
		Assertions.assertEquals("""
				<p>Some text</p>
				<p>Some key with args arg0 VAL1 VAL2</p>
				<p>Some other text</p>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<p>Some text</p>
				<p>${%someKeyWithArgs arg0,${VAR1},${VAR2}}</p>
				<p>Some other text</p>
				 	 """).bundle(TemplatesTest.class).
						variable("VAR1", "VAL1").
						variable("VAR2", "VAL2")
				));
	}
	
	@Test
	public void testTemplateFalseConditionInObjectInTrueCondition() {
		Assertions.assertEquals("""
				<p>Some text</p>


				<p>Name: Joe B</p>
				
				<p>No Age</p>
				
				 
				 
				<p>Some other text</p>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<p>Some text</p>
				<t:if aPerson>
				<t:object aPerson>
				<p>Name: ${name}</p>
				<t:if aAge>
				<p>Age: ${aAge}</p>
				<t:else/>
				<p>No Age</p>
				</t:if>
				</t:object>
				</t:if>
				<p>Some other text</p>
				 	 """).
				object("aPerson", (c) ->
					TemplateModel.ofContent(c).
						variable("name", "Joe B").
						variable("aAge", "")
				)));
		
	}
	
	@Test
	public void testTemplateObject() {
		Assertions.assertEquals("""
				<html>
				<body>
				<p>Some text</p>
				
				<p>Name: Joe B</p>
				<p>Age: 27</p>
				<p>Location: London</p>
				
				<p>Some other text</p>
				</body>
				</html>
				""", 
				createParser().process(TemplateModel.ofContent("""
				<html>
				<body>
				<p>Some text</p>
				<t:object aPerson>
				<p>Name: ${name}</p>
				<p>Age: ${age}</p>
				<p>Location: ${location}</p>
				</t:object>
				<p>Some other text</p>
				</body>
				</html>
				 	 """).
				object("aPerson", (c) ->
					TemplateModel.ofContent(c).
						variable("name", "Joe B").
						variable("age", "27").
						variable("location", "London")
				)));
		
	}
	
	private TemplateProcessor createParser() {
		return new TemplateProcessor.Builder().
				withLogger(new Logger() {
					
					@Override
					public void warning(String message, Object... args) {
						System.out.println(MessageFormat.format("[WARN] " + message, args));
						
					}
					
					@Override
					public void debug(String message, Object... args) {
						System.out.println(MessageFormat.format("[DEBUG] " + message, args));						
					}
				}).
				build();
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
