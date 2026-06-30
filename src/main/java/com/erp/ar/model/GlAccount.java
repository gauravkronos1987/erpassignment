package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "gl_account")
@Getter
@Setter
@NoArgsConstructor
public class GlAccount {

    @Id
    @GeneratedValue
    @Column(name = "gl_account_id")
    private UUID glAccountId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    private NormalBalance normalBalance;
}
