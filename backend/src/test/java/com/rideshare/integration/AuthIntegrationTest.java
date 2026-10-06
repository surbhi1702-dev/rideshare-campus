package com.rideshare.integration;

import com.rideshare.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerReturnsTokenAndProfileWithoutPasswordHash() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "name", "Rahul Sharma", "email", "Rahul.Sharma@IITBBS.ac.in", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("rahul.sharma@iitbbs.ac.in"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String storedHash = userRepository.findByEmailIgnoreCase("rahul.sharma@iitbbs.ac.in").orElseThrow().getPasswordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void nonInstituteEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "name", "Outsider", "email", "someone@gmail.com", "password", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_DOMAIN"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        registerStudent("asha");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "name", "Asha Again", "email", "ASHA@iitbbs.ac.in", "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void weakPasswordAndBadInputGiveFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "name", "X", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void loginWorksAndWrongPasswordIsUnauthorized() throws Exception {
        registerStudent("vikram");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "vikram@iitbbs.ac.in", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "vikram@iitbbs.ac.in", "password", "Wrong1234"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@iitbbs.ac.in", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mockMvc.perform(get("/api/rides"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/rides").header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileCanBeReadAndUpdated() throws Exception {
        TestUser user = registerStudent("meera");

        mockMvc.perform(authed(get("/api/users/me"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("meera@iitbbs.ac.in"));

        mockMvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me"), user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Meera Iyer", "phoneNumber", "+919876543210"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Meera Iyer"))
                .andExpect(jsonPath("$.phoneNumber").value("+919876543210"));
    }
}
