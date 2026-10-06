package com.rideshare.user;

import com.rideshare.user.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(),
                user.getPhoneNumber(), user.getRole(), user.getCreatedAt());
    }
}
