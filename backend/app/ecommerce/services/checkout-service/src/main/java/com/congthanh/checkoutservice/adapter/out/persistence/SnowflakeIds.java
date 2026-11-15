package com.congthanh.checkoutservice.adapter.out.persistence;

import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.checkoutservice.application.port.out.Ids;
import org.springframework.stereotype.Component;

@Component
class SnowflakeIds implements Ids {

    @Override
    public String newId() {
        return Snowflake.nextString();
    }
}
