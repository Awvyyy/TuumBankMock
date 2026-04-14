package org.example.tuumbankmock.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.example.tuumbankmock.model.Transaction;

import java.util.List;

@Mapper
public interface TransactionMapper {

    @Insert("""
        INSERT INTO transactions (
            account_id,
            idempotency_key,
            amount,
            currency,
            direction,
            description,
            balance_after_transaction
        )
        VALUES (
            #{accountId},
            #{idempotencyKey},
            #{amount},
            #{currency},
            #{direction},
            #{description},
            #{balanceAfterTransaction}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "transactionId")
    void insertTransaction(Transaction transaction);

    @Select("""
        SELECT
            transaction_id AS transactionId,
            account_id AS accountId,
            idempotency_key AS idempotencyKey,
            amount,
            currency,
            direction,
            description,
            balance_after_transaction AS balanceAfterTransaction
        FROM transactions
        WHERE account_id = #{accountId}
        ORDER BY transaction_id ASC
        """)
    List<Transaction> findByAccountId(Long accountId);

    @Select("""
        SELECT
            transaction_id AS transactionId,
            account_id AS accountId,
            idempotency_key AS idempotencyKey,
            amount,
            currency,
            direction,
            description,
            balance_after_transaction AS balanceAfterTransaction
        FROM transactions
        WHERE account_id = #{accountId}
          AND idempotency_key = #{idempotencyKey}
        """)
    Transaction findByAccountIdAndIdempotencyKey(Long accountId, String idempotencyKey);
}