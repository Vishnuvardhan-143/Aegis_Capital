package com.example.auth.service;

import com.example.auth.dto.UserProfileDto;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("test@test.com");
        testUser.setMobileNo("1234567890");
        testUser.setPanNo("ABCDE1234F");
    }

    @Test
    void getProfileByEmail_Success() {
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@test.com");

        assertEquals("Test User", profile.getName());
        assertEquals("test@test.com", profile.getEmail());
        assertEquals("1234567890", profile.getMobileNo());
    }

    @Test
    void getProfileByEmail_NotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.getProfileByEmail("missing@test.com"));
    }
}
