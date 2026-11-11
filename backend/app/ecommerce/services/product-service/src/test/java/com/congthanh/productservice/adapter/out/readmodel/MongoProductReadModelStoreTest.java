package com.congthanh.productservice.adapter.out.readmodel;

import com.congthanh.productservice.application.readmodel.ProductDocument;
import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoProductReadModelStoreTest {

    MongoTemplate mongo = mock(MongoTemplate.class);
    MongoProductReadModelStore store = new MongoProductReadModelStore(mongo);

    @Test
    void coreIsOnlyWrittenOverAnOlderVersion() {
        store.upsertCore(doc("p1", "shirt", 42L));

        var q = ArgumentCaptor.forClass(Query.class);
        var u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).upsert(q.capture(), u.capture(), eq("product"));
        assertThat(q.getValue().getQueryObject().toJson())
                .contains("\"_id\": \"p1\"", "\"coreVersion\": {\"$lt\": 42}", "\"coreVersion\": {\"$exists\": false}");
        String set = u.getValue().getUpdateObject().get("$set").toString();
        assertThat(set).contains("slug=shirt", "coreVersion=42").doesNotContain("prices", "rating", "stock");
    }

    @Test
    void newerStoredVersionMeansSkip() {
        when(mongo.upsert(any(Query.class), any(UpdateDefinition.class), eq("product")))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error index: _id_"));

        assertThat(store.upsertCore(doc("p1", "shirt", 1L))).isFalse();
    }

    @Test
    void slugTakenByStaleDocumentIsReleasedThenRetried() {
        when(mongo.upsert(any(Query.class), any(UpdateDefinition.class), eq("product")))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error index: slug_unique"))
                .thenReturn(null);

        assertThat(store.upsertCore(doc("p1", "shirt", 2L))).isTrue();

        var q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateMulti(q.capture(), any(Update.class), eq("product"));
        assertThat(q.getValue().getQueryObject().toJson()).contains("\"slug\": \"shirt\"", "\"$ne\": \"p1\"");
        verify(mongo, times(2)).upsert(any(Query.class), any(UpdateDefinition.class), eq("product"));
    }

    @Test
    void partialUpdatesAreGuardedPerKeyAndStaleOnesIgnored() {
        Instant at = Instant.ofEpochMilli(5_000);
        when(mongo.upsert(any(Query.class), any(UpdateDefinition.class), eq("product")))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error index: _id_"));

        store.applyPrice("p1", "us.retail", new BigDecimal("9.99"), "USD", at);
        store.applyStock("p1", "w1", 3, at);

        var q = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).upsert(q.capture(), any(UpdateDefinition.class), eq("product"));
        assertThat(q.getAllValues().get(0).getQueryObject().toJson()).contains("versions.price_us_retail");
        assertThat(q.getAllValues().get(1).getQueryObject().toJson()).contains("versions.stock_w1", "\"$lt\": 5000");
    }

    @Test
    void readsOnlyCompleteDocumentsAndSurviveMongoOutage() {
        when(mongo.findOne(any(Query.class), eq(ProductDocument.class), eq("product")))
                .thenThrow(new DataAccessResourceFailureException("down", new MongoException("down")));

        assertThat(store.findBySlug("shirt")).isEmpty();

        var q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(q.capture(), eq(ProductDocument.class), eq("product"));
        assertThat(q.getValue().getQueryObject().toJson()).contains("\"coreVersion\": {\"$exists\": true}");
    }

    private static ProductDocument doc(String id, String slug, long version) {
        ProductDocument d = new ProductDocument();
        d.setId(id);
        d.setSlug(slug);
        d.setCoreVersion(version);
        return d;
    }
}
