package com.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AdminController adminController;

    private final String AUTH_URL = "http://auth-backend:5052";
    private final String ACCOUNT_URL = "http://account-backend:5050";
    private final String TRANS_URL = "http://transaction-backend:5005";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminController, "authServiceUrl", AUTH_URL);
        ReflectionTestUtils.setField(adminController, "accountServiceUrl", ACCOUNT_URL);
        ReflectionTestUtils.setField(adminController, "transactionServiceUrl", TRANS_URL);
    }

    @Test
    void getAllUsers_Success() {
        Map<String, Object> user = new HashMap<>();
        user.put("email", "test@admin.com");
        ResponseEntity<List<Map<String, Object>>> mockResponse = new ResponseEntity<>(Collections.singletonList(user), HttpStatus.OK);

        when(restTemplate.exchange(
                eq(AUTH_URL + "/internal/admin/users"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        ResponseEntity<List<Map<String, Object>>> response = adminController.getAllUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));
    }

    @Test
    void getAccountStats_Success() {
        Map<String, Object> account1 = new HashMap<>();
        account1.put("balance", 1000.50);
        
        ResponseEntity<List<Map<String, Object>>> mockResponse = new ResponseEntity<>(Collections.singletonList(account1), HttpStatus.OK);

        when(restTemplate.exchange(
                eq(ACCOUNT_URL + "/internal/admin/accounts"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        ResponseEntity<Map<String, Object>> response = adminController.getAccountStats();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().get("count"));
        assertEquals(1000.50, response.getBody().get("totalLiquidity"));
    }

    @Test
    void applyMonthlyInterest_Success() {
        // Setup mock accounts
        Map<String, Object> account = new HashMap<>();
        account.put("id", 1L);
        account.put("balance", 1000.0);
        // Created date a few months ago to ensure interest applies
        account.put("createdAt", "2023-01-01T00:00:00"); 

        ResponseEntity<List<Map<String, Object>>> mockAccResponse = new ResponseEntity<>(Collections.singletonList(account), HttpStatus.OK);

        when(restTemplate.exchange(
                eq(ACCOUNT_URL + "/internal/admin/accounts"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockAccResponse);

        // Submitting
        ResponseEntity<Map<String, Object>> response = adminController.applyMonthlyInterest(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().get("accountsProcessed"));
        
        // Verify post calls
        verify(restTemplate, times(1)).postForEntity(contains("/apply-interest"), isNull(), eq(Void.class));
        verify(restTemplate, times(1)).postForEntity(contains("/record-interest"), isNull(), eq(Void.class));
    }
}
