# tinytemplate

![Maven Build/Test JDK 17](https://github.com/sshtools/tinytemplate/actions/workflows/maven.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.sshtools/tinytemplate/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.sshtools/tinytemplate)
[![Coverage Status](https://coveralls.io/repos/github/sshtools/tinytemplate/badge.svg)](https://coveralls.io/github/sshtools/tinytemplate)
[![javadoc](https://javadoc.io/badge2/com.sshtools/tinytemplate/javadoc.svg)](https://javadoc.io/doc/com.sshtools/tinytemplate)
![JPMS](https://img.shields.io/badge/JPMS-com.sshtools.tinytemplate-purple) 


![TinyTemplate](src/main/web/logo-no-background.png)

A lightweight Java string template engine. While it is intended to be used with HTML. it 
will work with any text content. While small, it has some unique features and is fast and 
flexible.

It requires just 6 HTML-like tags, and a bash-like variable expression syntax.

## Status

Feature complete. Just some test coverage to complete and more documentation.

## Features

 * No dependencies, JPMS compliant, Graal Native Image friendly
 * Fast. See Design Choices.
 * Simple Java. Public API consists of just 2 main classes, `TemplateModel` and `TemplateProcessor`.
 * Simple Content. Just `<t:if>` (and `<t:else>`), `<t:include>`, `<t:object>`, `<t:list>` and `<t:instruct/>`. Bash like variable such as `${myVar}`.
 * Internationalisation features.  
 
## Design Choices

 * No reflection. Better performance.
 * No expression language. All conditions are *named*, with the condition calculated in Java code.
 * Focus on HTML.
 * Avoids `String.replace()` and friends.
 * Single pass parser, use lambdas to compute template components only when they are actually needed.    

## Quick Start

Add the library to your project.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>tinytemplate</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Example

A simple example showing most of the features.

```java
public class Example1 {

    public static void main(String[] args) {
        System.out.println(new TemplateProcessor.Builder().
                build().process(TemplateModel.ofContent("""
            <html>
                <head>
                    <t:include cssImports/>
                </head>
                <body>
                    <h1>${%title}</h1>
                    
                    <p>The current time is ${time}</p>
                    <p>And 2 + 2 = ${answer}</p>
                    <p>Weather is ${weather}</p>
                    <p>I18n Text1: ${i18n1}</p>
                    <p>I18n Text2: ${i18n2}</p>
                    
                    <t:if am>
                        <p>Which is AM</p>
                    <t:else/>
                        <p>Which is PM</p>
                    </t:if>
                    
                    <t:if menu>
                        <ul>
                        <t:list menu>
                            <li>
                                <a href="${link}">Time warp to ${day}
                                    <t:if friday>
                                        <b>, it's party time!</b>
                                    </t:if>
                                </a>
                            </li>
                        </t:list>
                        <ul> 
                    </t:if>
                    
                    <t:object me>
                        <p>Name: ${name}</p>
                        <p>Age: ${age}</p>
                        <p>Location: ${location}</p>                        
                    </t:object>
                </body>
            </html>
                """).
            bundle(Example1.class).
            include("cssImports", TemplateModel.ofContent("<link src=\"styles.css\"/>")).
                variable("time", Example1::formatTime).
                variable("answer", () -> 2 + 2).
                variable("weather", "Sunny").
                i18n("i18n1", "key1").
                i18n("i18n2", "key2", Math.random()).
                condition("am", () -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 11).
                list("menu", content -> 
                    Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri").stream().map(day -> 
                        TemplateModel.ofContent(content).
                            variable("day", day).
                            variable("link", () -> "/warp-to>day=" + day).
                            condition("friday", () -> day.equals("Fri"))
                    ).toList()
                )).
                object("me", content -> TemplateModel.ofContent(content).
                    variable("name", "Joe B").
                    variable("age", 44).
                    variable("location", "London"))
            );
    }
    
    private static String formatTime() {
        return DateFormat.getDateTimeInstance().format(new Date());
    }
}

```

And a corresponding resource file, `Example1.properties`.

```
title=An Example
key1=Some Text
key2=Some other text with an argument. Random number is {0}
```

## Usage

### Variable Expansion

TinyTemplate supports a sort-of-like-Bash syntax for variable expansion. The exact
behaviour of each *string* replacement depends on a *parameter* and an *operator*.

The general syntax is `${parameter[<options>]}`. The *parameter* and any options are evaluated, then all text starting
with the `$` and ending with the `}` is substituted with the result.

Most patterns evaluate a named *parameter*. This can be any *condition*, *variable* or other type.

 * Evaluates to `true` when a *condition* of the same name evaluates to `true`  
 * Evaluates to `true` when a *variable* of the same name exists and is not an empty string.
 * Evaluates to `true` when any other type exists.

#### ${parameter}

Simplest type. Just always substitute with with value of a *variable* from the model.

```
${todaysDate}
```

#### ${parameter:?string:otherString}

If *parameter* evaluates to false as either a *variable* or *condition*, the expansion of *otherString* 
is substituted. Otherwise, the expansion of *string* is substituted.

```
${isPM:?Post Meridiem:Ante Meridiem noon}
``` 

#### ${parameter:-string}

If *parameter* evaluates to false as either a *variable* or *condition*, the expansion of *string* is substituted.
Otherwise, the value of *parameter* is substituted.

```
${location:-Unknown Location}
```

#### ${parameter:+string}

If *parameter* evaluates to false as either a *variable* or *condition*, an empty string is substituted, otherwise 
the expansion of *string* is substituted.

```
<input type="checked" ${selected:+checked} name="selected">
```

#### ${parameter:=string}

If *parameter* evaluates to false as either a *variable* or *condition*, the expansion of word is substituted, otherwise an empty string is substituted.

```
 <button type="button" ${clipboard-empty:=disabled} id="paste">Paste</button>
```

### Internationalisation

#### In A Template

A special form of Variable Expansion is used for internationalisation in a template. This supports arguments, as well as nested variables as arguments.

You must still set a `ResourceBundle` on the model for I18n keys in a template to work.

```java
var model = TemplateModel.ofContent(
	"""
	<p>${%someKey}</p>
	""").
	bundle(MyClass.class);
```

And `MyClass.properties` ..

```
someKey=Some internationalised text
```

##### Basic

The simplest syntax is `${%someKey}`, which will replace `someKey` with whatever the value is in the supplied `RessourceBundle`. 

##### With Arguments

To supply arguments, a comma separated list is used.  A comma is used by default, but any separator may be configured, and the separator may be escaped using a backslash `\`. 

For example `${%someKey arg0,arg 1 with space,arg 2 \, with comma}`

##### With Nested Variables

An argument can also be a nested variable that is available in the same scope as the replacement. 

Fixed text arguments and variables can be mixed in an I18N expression. However, you *cannot* currently mix text and variables in the same argument, for example `${%someKey prefix${var}suffix}` will *not* work.

For example `${%someKey arg0,${var1},${var2}`

#### In Code

An alternative way to do parameterised i18n messages is in the model. This is particular useful when the calculation of the message is complex or inconvenient to express as either simple strings in the template, or as argument variables.

In this case, you use the variable pattern `${someName}` instead of the I18n syntext with the `%` prefix.

```java

var model = TemplateModel.ofContent(
	"""
	<p>${someI18NVariable}</p>
	<small>${someOtherI18NVariable}</small>
	""").
	i18n("someI18NVariable", "keyInBundle").
	i18n("someOtherI18NVariable", "keyInBundleWithArgs", "arg0", "arg1").
	bundle(MyClass.class);
```

And the bundle ..

```
keyInBundle=Some internationalised text
keyInBundleWithARgs=Some internationalised text {0} {1}
```

As with most `TemplateModel` attributes, you can defer calculation of the template text by supplying the appropriate `Supplier<..>` instead of a direct object reference.

### Tags

TinyTemplates primary use is with HTML and fragments of HTML. Tags by default use an *XML* syntax so as to work well with code editors. Each tag starts with `t:`, so we suggest that you start all documents with the following header .. 

```html
<html lang="en" xmlns:t="https://jadaptive.com/t">
```

.. and all *fragments* of HTML with the following.

```html
<html lang="en" xmlns:t="https://jadaptive.com/t">
<t:instruct reset/>
```   

In both cases, the first line introduces the `t` namespace, so subsequent tags that appear in your document will not be marked as syntax errors by your editor.

The 2nd line used with fragments, will cause TinyTemplate to reset it's buffer, and forget any output so far collected. In effect, it will remove the first line. 

Depend on your editor, you may also need to complete the fragment with a closing `<html>` tag.

```html
<t:instruct end/>
</html>
```

This will prevent the template processor from writing any further output within that template, and so that closing tag will not appear in the processed HTML.

#### If / Else

Allows conditional inclusion of one or two blocks on content. Every condition in the template is assigned a *name*, which will be tied to a piece of Java code which produces whether it evaluates to
`true`.

```html
<t:if feelingFriendly>
    <p>Hello World!</p>
</t:if>
``` 

And the Java.

```java
model.condition("feelingFriendly", true);
```

You can also use `<t:else/>` to provide content that will be rendered when the condition evaluates
to `false`.

```html
<t:if feelingFriendly>
    <p>Hello World!</p>
<t:else/>
    <p>Go away world!</p>
</t:if>
``` 

And the Java.

```java
model.condition("feelingFriendly", false);
```

Conditions can be negated by prefixing the name with either a  `!` or the more XML syntax friendly `not`. 

```html
<t:if !feelingFriendly>
    <p>Humbug!</p>
</t:if>
```

If no such named condition exists, then checks will be made to see if a *Variable* with the same name
exists. If it doesn't exist, the condition will evaluate as `false`. If it does exist however, then  
it's result will depend on the value and it's type. 

 * `null` evaluates as false.
 * Empty string `""` evaluates as false.
 * Any number with a zero value evaluates as false.
 * An empty list evaluates as false, a list with any elements evaluates as true.
 * All other values evaluate as true.
 
If there is no such condition, and no such variable, then checks will be made to see if any such 
*Include* or *Object* exists.

```html
<t:if me>
    <t:object me>
        <p>Name : ${name}</p>
        <p>Age : ${age}</p>
        <p>Location : ${location}</p>
    </t:object>
<t:else/>
    <p>I don't know who I am</p>
</t:if>
```  

```java
if(me != null) {
    model.object("me", content -> TemplateModel.ofContent(content).
                    variable("name", "Joe B").
                    variable("age", 44).
                    variable("location", "London"));
}
```

#### Include

Includes allows templates to be nested. When a `<t:include my_include/>` tag is encountered in the template, a corresponding `my_include` is looked up in the current `TemplateModel`. This include itself, is a new `TemplateModel`, with it's own content (derived from a `String`, a resource, `Path` or whatever) as any other template.

The include tag would be a key tool if you were to use TinyTemplate to compose pages of lots of smaller parts. 

*An include must be completely self contained. It has no direct access to the template it is contained within. Like any other template, all variables (and potentially further nested includes) must be provided specifically to it.*

**main.html**

```html
<html lang="en" xmlns:t="https://jadaptive.com/t">
<body>
	<t:include nav_menu/>
	<p>My Main Content</p>
</body>
</html>	
```

**menu.frag.html**

```html
<html lang="en" xmlns:t="https://jadaptive.com/t">
<t:instruct reset/>
<ul>
	<t:list menu>
		<li><a href="${href}">${action}</a></li>
	</t>
<ul>
<t:instruct end/>
</html>
```

*Note, the use of `<t:instruct reset/>` and `<t:instruct end/>`. This is not strictly required, it is to help your IDE cope with fragments of HTML with custom tags. See above.*

**Main.java**

```java


public record Anchor(String href, String text) {}

// ...

var links = Set.of(
	new Anchor("file.html", "File"),
	new Anchor("edit.html", "Edit"),
	new Anchor("view.html", "View"),
	new Anchor("help.html", "Help")
);

var model = TemplateModel.ofResource(Main.class, "main.html").
 	model.object("nav_menu", content -> 
		TemplateModel.ofResource(Main.class, "menu.frag.html").
			list("menu", (content) ->
				links.stream().map(anchor -> TemplateModel.ofContent(content).
					variable("href", anchor::href).
					variable("text", anchor::text)
				).toList()
			)
    );
```

#### List

Lists allow blocks of content to be repeated, with different values for each row. Each list is assigned a *name*,
which ties it to the Java code that generates this list.

Each row of a list itself is a `TemplateModel`, which should be constructed from the `content` that is passed to 
to it. Every row of course can then contain any other *TinyTemplate* construct such as variables, includes, further
nested lists and so on.

For example,

```html
<h1>A list of ${number} people</h1>
<ul>
	<t:list people>
		<li>${_number} - ${name}, ${age} - ${locale}</li>
	</t:list>
</ul>
```

```java

public record Person(String name, int age, Locale locale) {}

// ...

var people = Set.of(
	new Person("Joe B", 44, Locale.ENGLISH),
	new Person("Maria Z", 27, Locale.GERMAN),
	new Person("Steve P", 31, Locale.US),
);
 
var model = TemplateModel.ofContent(html).
	variable("number", people.size()).
	list("people", (content) ->
		people.stream().map(person -> TemplateModel.ofContent(content).
			variable("name", person::name).
			variable("age", person::age).
			variable("locale", person.locale()::getDisplayName).
		).toList()
	);

```

Lists make some default variables available to each row. 

 * `_size`, the size of the list.
 * `_index`, the zero-based index of the current row.
 * `_number`, the number of the current row (i.e. `_index + 1`).
 
And some conditions.
 
 * `_first`, if the current row is the first row, will be `true`.
 * `_last`, if the current row is the last row, will be `true`.
 * `_odd`, if the index of the current row is an odd number, will be `true`.
 * `_even`, if the index of the current row is an even number, will be `true`.

#### Object

The object tag provides scope to a block a template text. The primary use for this would be to allow the same variable name to be used in more than one place in the current template, making it practical to create reusable `TemplateModel` instances, that for example map to a particular Java object. You can of course do this with the `<t:include>` tag, but `<t:object>` does not require a separate template resource.

Unlike `<t:include>`, it also inherits variables and conditions that exist in it's parent template. Any variables or conditions used, if they do not exist in the objects `TemplateModel`, the parent model will also be queried.

#### Instruct

Instructions are generic commands that can be sent to either the `TemplateProcessor` or some user code.

Built-in instructions currently consists of `<t:instruct reset/>` and `<t:instruct end/>` that you have seen elsewhere in this document.

Currently, user supplied instructions may not alter the template or the processors behaviour in any way. So, they can only be used to supply additional functions that affect the template as whole. This makes their use limited.

In the future, a richer API to create custom tags and processing may be provided.