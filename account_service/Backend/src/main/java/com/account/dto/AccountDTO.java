package com.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountDTO {
    private Long id;
    private String accno;
    private String ifsccode;
    private String bankname;
    private BigDecimal balance;
    private String userEmail;
    private Long version;
    private LocalDateTime createdAt;
}

