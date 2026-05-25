package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.response.ApiResponse;
import com.swiftpay.ledger.response.PagedResponse;
import com.swiftpay.ledger.dto.TransactionHistoryItem;
import com.swiftpay.ledger.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side history API for transactions.
 *
 * <p>Returns rows where the user appears as either the sender or the
 * receiver. {@code @Validated} on the class enables {@code @Min} validation
 * on the path variable (it would be ignored without it).</p>
 */
@RestController
@RequestMapping("/v1/transactions")
@Validated
@Tag(name = "Transactions", description = "Transaction history endpoints")
public class TransactionController {

    private final TransactionHistoryService transactionHistoryService;

    /** Constructor-based dependency injection. */
    public TransactionController(TransactionHistoryService transactionHistoryService) {
        this.transactionHistoryService = transactionHistoryService;
    }

    /**
     * Get one page of a user's transaction history.
     *
     * @param userId   target user (sender or receiver); must be positive
     * @param pageable {@code page=}, {@code size=}, {@code sort=} from the query string.
     *                 Default page is {@code 0}, size {@code 20}, sort by {@code createdAt DESC}.
     */
    @GetMapping("/{userId}")
    @Operation(
            summary = "Get a user's transaction history",
            description = "Returns transactions where the user is either sender or receiver, "
                          + "newest first. Supports page / size / sort query parameters."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "History page"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid userId or pageable")
    })
    public ResponseEntity<ApiResponse<PagedResponse<TransactionHistoryItem>>> history(
            @Parameter(description = "User ID (positive integer)", example = "1", required = true)
            @PathVariable
            @Min(value = 1, message = "userId must be positive")
            Long userId,

            @Parameter(description = "Pageable — defaults page=0, size=20, sort=created_at,DESC")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            @Schema(hidden = true)
            Pageable pageable
    ) {
        PagedResponse<TransactionHistoryItem> page = transactionHistoryService.getHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}