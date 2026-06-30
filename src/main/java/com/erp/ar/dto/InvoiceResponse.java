package com.erp.ar.dto;

import com.erp.ar.model.Invoice;
import com.erp.ar.model.InvoiceLineItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class InvoiceResponse {
    private UUID invoiceId;
    private String invoiceNumber;
    private String status;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String transactionCurrency;
    private BigDecimal subtotal;
    private BigDecimal taxTotal;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal amountOutstanding;
    private Integer version;
    private List<LineItemResponse> lineItems;
    private List<PaymentHistoryEntry> paymentHistory;

    public static InvoiceResponse from(Invoice inv, List<PaymentHistoryEntry> history) {
        return InvoiceResponse.builder()
            .invoiceId(inv.getInvoiceId())
            .invoiceNumber(inv.getInvoiceNumber())
            .status(inv.getStatus().name())
            .invoiceDate(inv.getInvoiceDate())
            .dueDate(inv.getDueDate())
            .transactionCurrency(inv.getTransactionCurrency())
            .subtotal(inv.getSubtotal())
            .taxTotal(inv.getTaxTotal())
            .totalAmount(inv.getTotalAmount())
            .amountPaid(inv.getAmountPaid())
            .amountOutstanding(inv.getAmountOutstanding())
            .version(inv.getVersion())
            .lineItems(inv.getLineItems().stream().map(LineItemResponse::from).collect(Collectors.toList()))
            .paymentHistory(history)
            .build();
    }

    @Getter
    @Builder
    public static class LineItemResponse {
        private UUID lineItemId;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public static LineItemResponse from(InvoiceLineItem li) {
            return LineItemResponse.builder()
                .lineItemId(li.getLineItemId())
                .description(li.getDescription())
                .quantity(li.getQuantity())
                .unitPrice(li.getUnitPrice())
                .lineTotal(li.getLineTotal())
                .build();
        }
    }

    @Getter
    @Builder
    public static class PaymentHistoryEntry {
        private UUID paymentId;
        private LocalDate paymentDate;
        private BigDecimal allocatedAmount;
    }
}
