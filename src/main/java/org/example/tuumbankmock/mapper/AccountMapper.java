package org.example.tuumbankmock.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.example.tuumbankmock.model.Account;

@Mapper
public interface AccountMapper {

    @Insert("""
            INSERT INTO accounts (customer_id, country)
            VALUES (#{customerId}, #{country})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "accountId")
    void insertAccount(Account account);

    @Select("""
            SELECT account_id AS accountId,
                   customer_id AS customerId,
                   country
            FROM accounts
            WHERE account_id = #{accountId}
            """)
    Account findById(Long accountId);
}