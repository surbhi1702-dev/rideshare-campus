package com.rideshare.user;

import com.rideshare.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDomainValidatorTest {

    private final EmailDomainValidator validator = new EmailDomainValidator(new AppProperties("Asia/Kolkata",
            new AppProperties.Auth(List.of(" IITBBS.ac.in ", "example.edu")),
            new AppProperties.Jwt("unused", 60),
            new AppProperties.Cors(List.of("http://localhost")),
            new AppProperties.Admin(null, null, null),
            new AppProperties.Ride(7, 60, 0.5, 90, 60, 3000)));

    @Test
    void acceptsConfiguredDomainsCaseInsensitively() {
        assertThat(validator.isAllowed("student@iitbbs.ac.in")).isTrue();
        assertThat(validator.isAllowed("Student@IITBBS.AC.IN")).isTrue();
        assertThat(validator.isAllowed("someone@example.edu")).isTrue();
    }

    @Test
    void acceptsSubdomainsOfConfiguredDomain() {
        assertThat(validator.isAllowed("student@cse.iitbbs.ac.in")).isTrue();
    }

    @Test
    void rejectsOtherDomainsAndLookalikes() {
        assertThat(validator.isAllowed("student@gmail.com")).isFalse();
        assertThat(validator.isAllowed("student@fakeiitbbs.ac.in")).isFalse();
        assertThat(validator.isAllowed("student@iitbbs.ac.in.evil.com")).isFalse();
        assertThat(validator.isAllowed("@iitbbs.ac.in")).isFalse();
        assertThat(validator.isAllowed(null)).isFalse();
    }
}
