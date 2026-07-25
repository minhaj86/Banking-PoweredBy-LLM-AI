package com.example.loans.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public record ApplicationInput(
        @NotBlank @Size(max=60) String productId, @Min(1) int productVersion,
        @NotNull @JsonDeserialize(using=DecimalStringDeserializer.class) String requestedAmount,
        @Pattern(regexp="SEK") @NotNull String currency, @Min(1) @Max(600) int termMonths,
        @NotBlank @Size(max=200) String purpose,
        @NotNull @Valid Identity identity, @NotNull @Valid Employment employment,
        @NotNull @Valid Income income, @NotNull @Valid Expenses expenses,
        @NotNull @Size(max=50) List<@Valid Debt> debts,
        @NotNull @Valid Kyc kyc, @NotNull @Valid Privacy privacy) {
    public record Identity(@NotBlank @Size(max=200) String legalName, @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Size(max=100) String identityIdentifier, @Email @NotBlank @Size(max=254) String email,
            @NotBlank @Size(max=40) String phone, @NotBlank @Size(max=500) String address,
            @NotBlank @Pattern(regexp="[A-Z]{2}") String countryOfResidence) {}
    public record Employment(@NotBlank @Pattern(regexp="EMPLOYED|SELF_EMPLOYED|RETIRED|UNEMPLOYED|OTHER") String type,
            LocalDate startDate, @Size(max=200) String employer) {}
    public record Income(@JsonDeserialize(using=DecimalStringDeserializer.class) String grossAmount,
            @JsonDeserialize(using=DecimalStringDeserializer.class) String netAmount,
            @NotBlank @Pattern(regexp="MONTHLY|ANNUAL|WEEKLY|IRREGULAR") String frequency,
            @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
            boolean variable, @NotNull @Size(max=20) List<@Valid OtherIncome> otherSources) {}
    public record OtherIncome(@NotBlank @Size(max=100) String kind, @JsonDeserialize(using=DecimalStringDeserializer.class) String netAmount,
            @NotBlank String frequency, @NotBlank String currency, boolean variable) {}
    // No field for combined living costs and debt: debt repayments are recorded only in debts.
    public record Expenses(@JsonDeserialize(using=DecimalStringDeserializer.class) String housing,
            @JsonDeserialize(using=DecimalStringDeserializer.class) String otherNonDebt,
            @Min(1) @Max(30) int householdSize, @NotBlank String currency, boolean excludesDebtRepayments) {}
    public record Debt(@NotBlank @Size(max=200) String creditor, @NotBlank @Size(max=40) String type,
            @JsonDeserialize(using=DecimalStringDeserializer.class) String outstandingBalance,
            @JsonDeserialize(using=DecimalStringDeserializer.class) String creditLimit,
            @JsonDeserialize(using=DecimalStringDeserializer.class) String utilisedBalance,
            @JsonDeserialize(using=DecimalStringDeserializer.class) String monthlyRepayment,
            @NotBlank String currency, @Min(0) Integer remainingMonths,
            @NotBlank @Pattern(regexp="APPLICANT_DECLARED") String source) {}
    public record Kyc(@NotBlank @Size(max=60) String questionnaireVersion, @NotNull @PastOrPresent Instant answeredAt,
            @NotBlank @Size(max=500) String relationshipPurpose, @NotBlank @Size(max=500) String sourceOfFunds,
            @NotEmpty @Size(max=10) List<@Pattern(regexp="[A-Z]{2}") String> taxResidencies, boolean pepDeclared) {}
    public record Privacy(@NotBlank @Size(max=60) String noticeVersion, @NotNull @PastOrPresent Instant acknowledgedAt,
            boolean financialEvidenceProcessingAcknowledged) {}
}
