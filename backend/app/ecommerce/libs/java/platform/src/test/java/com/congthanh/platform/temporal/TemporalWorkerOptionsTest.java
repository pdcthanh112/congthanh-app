package com.congthanh.platform.temporal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalWorkerOptionsTest {

    @Test
    void activityWorkersFollowSpringVirtualThreadsSetting() {
        var on = TemporalAutoConfiguration.workerOptions(new MockEnvironment().withProperty("spring.threads.virtual.enabled", "true"));
        assertThat(on.isUsingVirtualThreadsOnActivityWorker()).isTrue();
        assertThat(on.isUsingVirtualThreadsOnLocalActivityWorker()).isTrue();
        assertThat(on.isUsingVirtualThreadsOnWorkflowWorker()).isFalse();

        var off = TemporalAutoConfiguration.workerOptions(new MockEnvironment());
        assertThat(off.isUsingVirtualThreadsOnActivityWorker()).isFalse();
    }
}
