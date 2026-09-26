package com.fiapx.videoapi;

import static java.nio.file.Files.writeString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
class VideoApiApplicationTests extends AbstractSqsIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.web.context.WebApplicationContext context;

  @Test
  void contextLoadsAndExportsActualOpenApiContract() throws Exception {
    var mvc = webAppContextSetup(context).build();
    var response = mvc.perform(get("/v3/api-docs")).andExpect(MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
    writeString(java.nio.file.Path.of("target/openapi.json"), response.getContentAsString());
  }
}
