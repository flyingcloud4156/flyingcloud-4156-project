# COMS-4156-Project
This is the GitHub repository for the **service portion** of the Team Project associated with COMS 4156 Advanced Software Engineering. Our team name is flycloud and the following are our members: Ziheng Huang, Zhelin Fan, Jingyi Wang, and xxx.

## Overview
It is a financial management and settlement service that streamlines expense tracking, budgeting, and multi-party debt settlement. It allows users to record, categorize, and analyze transactions through a unified API that supports different clients—from personal trip-splitting and dorm bill apps to workplace reimbursement systems. Built with Java 17 and Spring Boot, it integrates PostgreSQL/MySQL databases, offers documented RESTful APIs via Swagger UI, and supports automated testing and deployment through Docker and Google Cloud.

## Building and Running Instructions
In order to build this project you must install the following:
1. Maven 3.9.9: https://maven.apache.org/download.cgi
   Download and follow the instructions for MacOS or Window, and set the bin as a new path variable by editing the system variables .
3. JDK 17: https://www.oracle.com/java/technologies/javase/jdk17-0-13-later-archive-downloads.html 
   The project used JDK17 for development. It is recommended to use JDK 17 or another suitable version of the JDK.
4. IntelliJ IDE: https://www.jetbrains.com/idea/download/?section=mac
   IntelliJ is the recommended IDE, but you are welcome to use any alternative that suits you.
After you download on your local machine, you can open IntelliJ IDEA to clone the Github repo. 
1. Click the green code button, copy the http line and give it to your IDE to clone.
   [img]
2. After cloning the repo, you can build the project with Maven using the following command.
   <code>mvn clean install</code>
3. Then you can run the project using the command.
   <code>mvn compile</code>
   
   <code>mvn spring-boot:run</code>
   
## Running a Cloud Based Instance
You can reach our service through cloud.

## Endpoints of First Iteration


## Unit Testing
- Testing framework: JUnit. 
- Mocking framework: Mockito. 
- Configuration files: pom.xml(Main confiduration), Mockito to enable inline mocking (src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker).
The testing and mocking frameworks are configured via Maven dependencies defined in pom.xml. No additional manual setup is required — Maven automatically downloads and integrates JUnit and Mockito during the build process.

Our unit tests are located under the directory 'src/test'. You can choose any of the classes present in the src/test directory and click run to see the results.


## API Testing
We use automated Bash scripts for API-level testing to validate the service endpoints and ensure consistent behavior across multiple clients.
Bash Script File: [final_api_tests_fixed.sh]()
Result and Report File: [api_test_results.md]()
To run the full automated API test suite:

<code>bash final_api_tests_fixed.sh</code>

## Style Check Report
We used the tool "checkstyle" ...to check the style of our code and generate style checking. ruleset.

## Branch Coverage Report

## Static Code Analysis

## Tool Used

