package com.example.login;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:login-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LoginIsolationTest {
    @Autowired MockMvc mvc;

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/login").with(csrf())
                .param("username", username).param("password", "Demo123!"))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test void usersAreIsolatedAndLogoutOnlyEndsOwnSession() throws Exception {
        var first = login("zhangsan");
        var second = login("lisi");
        assertNotEquals(first.getId(), second.getId());
        mvc.perform(get("/api/me").session(first).param("username", "lisi"))
            .andExpect(jsonPath("$.username").value("zhangsan"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/me").session(second)).andExpect(jsonPath("$.username").value("lisi"));
        mvc.perform(post("/api/logout").session(first).with(csrf())).andExpect(status().isOk());
        assertTrue(first.isInvalid());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(second)).andExpect(jsonPath("$.username").value("lisi"));
    }

    @Test void rejectsBadPasswordAndMissingCsrfAndRotatesSessionId() throws Exception {
        mvc.perform(post("/api/login").with(csrf()).param("username", "zhangsan").param("password", "wrong"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").param("username", "zhangsan").param("password", "Demo123!"))
            .andExpect(status().isForbidden());
        var anonymous = new MockHttpSession();
        var previousId = anonymous.getId();
        mvc.perform(post("/api/login").session(anonymous).with(csrf())
            .param("username", "zhangsan").param("password", "Demo123!"))
            .andExpect(status().isOk());
        assertNotEquals(previousId, anonymous.getId());
    }
}
