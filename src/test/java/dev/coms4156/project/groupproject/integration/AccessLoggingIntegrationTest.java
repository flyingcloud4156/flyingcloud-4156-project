// package dev.coms4156.project.groupproject.integration;
//
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//
// import ch.qos.logback.classic.Logger;
// import ch.qos.logback.classic.spi.ILoggingEvent;
// import ch.qos.logback.core.read.ListAppender;
// import dev.coms4156.project.groupproject.Application;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.http.MediaType;
// import org.springframework.test.context.ActiveProfiles;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import org.springframework.web.context.WebApplicationContext;
//
//// TODO: This testing has problems. should have a H2 Database for testing OR connect to your
//// Database beforehand. @chihiro1412
//// ✦ 这个错误日志在 mvn install 过程中出现是不正常的，它暴露了现有测试代码中的一个问题。
////
//// 简单来说，原因如下：
////
////        1. 这是一个集成测试：从日志堆栈的 AccessLoggingIntegrationTest.testUserAuthEndpoints_logged
//// 这一行可以看出，这个错误发生在一个集成测试中，而不是我们刚才写的单元测试里。
////        2. 测试试图连接真实数据库：该集成测试正在尝试调用真实的登录接口 (/api/v1/auth/login)。这个操作最终会触发
//// UserServiceImpl 去数据库里查询用户。
////        3. 数据库不可用：在 mvn install 的测试环境中，没有一个正在运行的、可连接的 mysql 数据库。因此，数据库查询失败了。
////        4. 错误被转换：UserServiceImpl 在捕获到数据库查询失败（很可能返回了 null）后，将其转换为一个业务异常
//// RuntimeException: User not found 并抛出。
////
//// 结论：
////
//// 这个错误虽然没有导致整个构建失败（因为测试用例没有正确地处理或断言这个异常），但它明确地表明了现有的集成测试是
//// 不可靠的。它依赖于外部环境（一个运行中的数据库），这违背了测试应有的独立性和确定性。
////
//// 要修复这个问题 正确的做法是：
////
//// 为集成测试配置一个专门的内存数据库（如 H2 Database）。这样，测试在运行时会启动一个临时的、干净的数据库，测试结
//// 束后再销毁它，从而保证集成测试的稳定和独立。
//
/// ** Integration tests to verify all API entry points are correctly logged. */
// @SpringBootTest(classes = Application.class)
// @AutoConfigureWebMvc
// @ActiveProfiles("test")
// class AccessLoggingIntegrationTest {
//
//  @Autowired private WebApplicationContext webApplicationContext;
//
//  private MockMvc mockMvc;
//  private ListAppender<ILoggingEvent> listAppender;
//  private Logger accessLogger;
//
//  @BeforeEach
//  void setUp() {
//    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
//
//    // Set up log capture for the access logger
//    accessLogger =
//        (Logger) org.slf4j.LoggerFactory.getLogger("dev.coms4156.project.groupproject.access");
//    listAppender = new ListAppender<>();
//    listAppender.start();
//    accessLogger.addAppender(listAppender);
//  }
//
//  @Test
//  void testUserAuthEndpoints_logged() throws Exception {
//    // Test register endpoint
//    mockMvc.perform(
//        post("/api/v1/auth/register")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"email\":\"test@example.com\",\"password\":\"password\"}"));
//
//    // Test login endpoint
//    mockMvc.perform(
//        post("/api/v1/auth/login")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"email\":\"test@example.com\",\"password\":\"password\"}"));
//
//    // Verify logs were generated for auth endpoints
//    long authLogs =
//        listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("event=ACCESS"))
//            .filter(event -> event.getFormattedMessage().contains("/api/v1/auth/"))
//            .count();
//
//    assert authLogs >= 2 : "Expected at least 2 auth endpoint logs";
//  }
//
//  @Test
//  void testUserEndpoints_logged() throws Exception {
//    // Test users lookup endpoint
//    mockMvc.perform(get("/api/v1/users:lookup").param("query", "test@example.com"));
//
//    // Test currencies endpoint
//    mockMvc.perform(get("/api/v1/currencies"));
//
//    // Verify logs were generated for user endpoints
//    long userLogs =
//        listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("event=ACCESS"))
//            .filter(
//                event ->
//                    event.getFormattedMessage().contains("/api/v1/users")
//                        || event.getFormattedMessage().contains("/api/v1/currencies"))
//            .count();
//
//    assert userLogs >= 2 : "Expected at least 2 user endpoint logs";
//  }
//
//  @Test
//  void testLedgerEndpoints_logged() throws Exception {
//    // Test ledgers endpoint
//    mockMvc.perform(
//        post("/api/v1/ledgers")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"name\":\"Test Ledger\"}"));
//
//    // Test ledgers mine endpoint
//    mockMvc.perform(get("/api/v1/ledgers:mine"));
//
//    // Verify logs were generated for ledger endpoints
//    long ledgerLogs =
//        listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("event=ACCESS"))
//            .filter(event -> event.getFormattedMessage().contains("/api/v1/ledgers"))
//            .count();
//
//    assert ledgerLogs >= 2 : "Expected at least 2 ledger endpoint logs";
//  }
//
//  @Test
//  void testLogFormat_containsAllRequiredFields() throws Exception {
//    // Simulate an access log event
//    mockMvc.perform(get("/api/v1/currencies"));
//
//    // Find the access log entry
//    ILoggingEvent accessLog =
//        listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("event=ACCESS"))
//            .findFirst()
//            .orElseThrow(() -> new AssertionError("No access log found"));
//
//    String message = accessLog.getFormattedMessage();
//
//    // Verify required fields are present
//    assert message.contains("event=ACCESS") : "Missing event field";
//    assert message.contains("method=GET") : "Missing method field";
//    assert message.contains("uri=/api/v1/currencies") : "Missing URI field";
//    assert message.contains("handler=") : "Missing handler field";
//    assert message.contains("status=") : "Missing status field";
//    assert message.contains("durationMs=") : "Missing duration field";
//    assert message.contains("user=") : "Missing user field";
//    assert message.contains("exception=") : "Missing exception field";
//  }
//
//  @Test
//  void testAllApiEndpoints_generateAccessLogs() throws Exception {
//    // Test a representative sample of all endpoint types
//    mockMvc.perform(get("/api/v1/currencies"));
//    mockMvc.perform(
//        post("/api/v1/auth/login")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"email\":\"test@example.com\",\"password\":\"password\"}"));
//    mockMvc.perform(
//        post("/api/v1/ledgers")
//            .contentType(MediaType.APPLICATION_JSON)
//            .content("{\"name\":\"Test\"}"));
//    mockMvc.perform(get("/api/v1/users:lookup").param("query", "test"));
//
//    // Count total access logs
//    long totalAccessLogs =
//        listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("event=ACCESS"))
//            .count();
//
//    assert totalAccessLogs >= 4
//        : String.format("Expected at least 4 access logs, but got %d", totalAccessLogs);
//
//    // Verify all logs have the correct format
//    boolean allLogsValid =
//        listAppender.list.stream()
//            .filter(event -> event.getFormattedMessage().contains("event=ACCESS"))
//            .allMatch(
//                event -> {
//                  String msg = event.getFormattedMessage();
//                  return msg.contains("method=")
//                      && msg.contains("uri=")
//                      && msg.contains("status=")
//                      && msg.contains("durationMs=")
//                      && msg.contains("user=");
//                });
//
//    assert allLogsValid : "Not all access logs contain required fields";
//  }
// }
