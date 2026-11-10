package com.congthanh.productservice.application.service;

import com.congthanh.productservice.application.service.AbstractCircuitBreakFallbackHandler;
import com.congthanh.productservice.application.port.in.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl extends AbstractCircuitBreakFallbackHandler implements MediaService {
}
