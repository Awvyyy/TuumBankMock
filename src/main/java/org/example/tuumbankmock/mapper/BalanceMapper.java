package org.example.tuumbankmock.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.tuumbankmock.model.Balance;
import org.example.tuumbankmock.model.Currency;
import java.util.List;

@Mapper
public interface BalanceMapper {

    @Insert("""
            INSERT INTO balances (account_id, currency, available_amount)
            VALUES (#{accountId}, #{currency}, #{availableAmount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "balanceId")
    void insertBalance(Balance balance);

    @Select("""
            SELECT balance_id AS balanceId,
                   account_id AS accountId,
                   currency,
                   available_amount AS availableAmount
            FROM balances
            WHERE account_id = #{accountId}
            """)
    List<Balance> findByAccountId(Long accountId);

    @Select("""
            SELECT balance_id AS balanceId,
                   account_id AS accountId,
                   currency,
                   available_amount AS availableAmount
            FROM balances
            WHERE account_id = #{accountId}
              AND currency = #{currency}
            """)
    Balance findByAccountIdAndCurrency(Long accountId, Currency currency);

    @Update("""
            UPDATE balances
            SET available_amount = #{availableAmount}
            WHERE balance_id = #{balanceId}
            """)
    void updateBalance(Balance balance);
}