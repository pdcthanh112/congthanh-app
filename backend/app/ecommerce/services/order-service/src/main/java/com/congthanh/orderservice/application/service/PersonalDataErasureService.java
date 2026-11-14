package com.congthanh.orderservice.application.service;

import com.congthanh.orderservice.application.port.in.PersonalDataErasure;
import com.congthanh.orderservice.application.port.out.PersonalDataStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalDataErasureService implements PersonalDataErasure {

    private final PersonalDataStore store;

    public PersonalDataErasureService(PersonalDataStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void erase(String requestId, String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return;
        }
        String retained = store.anonymize(subjectId);
        store.confirmErasure(requestId, subjectId, retained);
    }
}
