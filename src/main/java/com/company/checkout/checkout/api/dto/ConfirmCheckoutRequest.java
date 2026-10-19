package com.company.checkout.checkout.api.dto;

import com.company.checkout.common.util.MaskingUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries a tokenised payment method (never a raw card number — keep the service out of PCI scope).
 */
public record ConfirmCheckoutRequest(@NotBlank @Size(max = 128) String paymentToken) {

    @Override
    public String toString() {
        return "ConfirmCheckoutRequest[paymentToken=" + MaskingUtil.maskToken(paymentToken) + "]";
    }
}
