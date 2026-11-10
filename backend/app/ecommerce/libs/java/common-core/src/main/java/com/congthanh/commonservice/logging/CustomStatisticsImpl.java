package com.congthanh.commonservice.logging;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.internal.StatisticsImpl;

@Slf4j
public class CustomStatisticsImpl extends StatisticsImpl {

    public CustomStatisticsImpl(
            SessionFactoryImplementor sessionFactory
    ) {
        super(sessionFactory);
    }
}
