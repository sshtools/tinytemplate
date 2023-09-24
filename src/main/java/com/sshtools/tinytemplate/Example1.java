package com.sshtools.tinytemplate;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import com.sshtools.tinytemplate.Templates.TemplateModel;
import com.sshtools.tinytemplate.Templates.TemplateProcessor;

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
			include("cssImports", TemplateModel.ofContent("<link src=\"styles.css\"/>")).
			variable("time", Example1::formatTime).
			variable("answer", () -> 2 + 2).
			variable("weather", "Sunny").
			condition("am", () -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 11).
			list("menu", content -> 
				Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri").stream().map(day -> 
				 	TemplateModel.ofContent(content).
				 		variable("day", day).
				 		variable("link", () -> "/warp-to>day=" + day).
				 		condition("friday", () -> day.equals("Fri"))
				).toList()
			)));
	}
	
	private static String formatTime() {
		return DateFormat.getDateTimeInstance().format(new Date());
	}
}
