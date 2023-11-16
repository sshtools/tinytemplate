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

It requires just 5 HTML-like tags, and a bash-like variable expression syntax.

## Status

Feature complete. Just some test coverage to complete and addition of Javadoc.

## Features

 * No dependencies, JPMS compliant, Graal Native Image friendly
 * Fast. Single pass parser, use lambdas to compute template components only when
   they are actually needed.
 * Simple Java. Public API consists of just 2 main classes, `TemplateModel` and `TemplateProcessor`.
 * Simple Content. Just `<t:if>` (and `<t:else>`), `<t:include>`, `<t:template>` and `<t:list>`. Bash like variable such as `${myVar}`.
 * Internationalisation features.       

## Quick Start

Add the library to your project.

```xml
<dependency>
    <groupId>com.sshtools</groupId>
    <artifactId>tinytemplate</artifactId>
    <version>0.9.2</version>
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
                ))
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

 

