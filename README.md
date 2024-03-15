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
    <version>0.9.3</version>
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
                object("me", content -> Template.ofContent(content).
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

### Tags

TinyTemplates primary use is with HTML and fragments of HTML. Tags by default use an *XML* syntax so as to
work well with code editors. Each tag starts with `t:`, so we suggest that you start all documents 
with the following header .. 

```html
<html lang="en" xmlns:t="https://jadaptive.com/t">
```

.. and all *fragments* of HTML with the following.

```html
<html lang="en" xmlns:t="https://jadaptive.com/t">
<t:instruct reset/>
```   

In both cases, the first line introduces the `t` namespace, so subsequent tags that appear in your
document will not be marked as syntax errors by your editor.

The 2nd line used with fragments, will cause TinyTemplate to reset it's buffer, and forget any output
so far collected. In effect, it will remove the first line. 

#### If / Else

Allows conditional inclusion of one or two blocks on content. Every condition in the template is
assigned a *name*, which will be tied to a piece of Java code which produces whether it evaluates to
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

If no such named condition exists, then checks will be made to see if a *Variable* with the same name
exists. If it doesn't exist, the condition will evaluate as `false`. If it does exist however, then  
it's result will depend on the value and it's type. 

 * `null` evaluates as false.
 * Empty string `""` evaluates as false.
 * Any number with a zero value evaluates as false.
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
    model.object("me", content -> Template.ofContent(content).
                    variable("name", "Joe B").
                    variable("age", 44).
                    variable("location", "London"));
}
```

#### Include

TODO

#### List

TODO

#### Object

TODO

#### Instruct

TODO