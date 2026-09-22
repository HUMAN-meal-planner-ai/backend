package com.human.backend.integration.aiserver;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.human.backend.prediction.dto.request.AiPricePredictionRequest;
import com.human.backend.prediction.dto.response.AiPricePredictionBatchResponse;

@Component
public class AiPricePredictionClient {

    private final RestClient restClient;

    public AiPricePredictionClient(
            @Value("${ai.server.url:http://localhost:8000}") String aiServerUrl,
            @Value("${ai.server.connect-timeout:3s}") Duration connectTimeout,
            @Value("${ai.server.read-timeout:10s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(aiServerUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public AiPricePredictionBatchResponse predictNext(List<Long> seriesIds) {
        return predict("/api/v1/price-predictions/next", seriesIds);
    }

    public AiPricePredictionBatchResponse predictSevenDays(List<Long> seriesIds) {
        return predict("/api/v1/price-predictions/7-days", seriesIds);
    }

    private AiPricePredictionBatchResponse predict(String uri, List<Long> seriesIds) {
        try {
            return restClient.post()
                    .uri(uri)
                    .body(new AiPricePredictionRequest(List.copyOf(seriesIds)))
                    .retrieve()
                    .body(AiPricePredictionBatchResponse.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw failure(AiPricePredictionClientException.FailureType.NOT_FOUND,
                    "AI 가격예측 서버에서 요청한 시계열의 실측 가격을 찾지 못했습니다.", exception);
        } catch (HttpServerErrorException exception) {
            throw failure(AiPricePredictionClientException.FailureType.SERVER_ERROR,
                    "AI 가격예측 서버가 요청을 처리하지 못했습니다.", exception);
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCause(exception, HttpConnectTimeoutException.class)) {
                throw failure(AiPricePredictionClientException.FailureType.TIMEOUT,
                        "AI 가격예측 서버 호출 시간이 초과되었습니다.", exception);
            }
            if (hasCause(exception, ConnectException.class)) {
                throw failure(AiPricePredictionClientException.FailureType.UNAVAILABLE,
                        "AI 가격예측 서버에 연결할 수 없습니다.", exception);
            }
            throw failure(AiPricePredictionClientException.FailureType.UNAVAILABLE,
                    "AI 가격예측 서버 통신에 실패했습니다.", exception);
        } catch (RestClientException exception) {
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCause(exception, HttpConnectTimeoutException.class)) {
                throw failure(AiPricePredictionClientException.FailureType.TIMEOUT,
                        "AI 가격예측 서버 호출 시간이 초과되었습니다.", exception);
            }
            if (hasCause(exception, ConnectException.class)) {
                throw failure(AiPricePredictionClientException.FailureType.UNAVAILABLE,
                        "AI 가격예측 서버에 연결할 수 없습니다.", exception);
            }
            throw failure(AiPricePredictionClientException.FailureType.INVALID_RESPONSE,
                    "AI 가격예측 서버 응답을 처리할 수 없습니다.", exception);
        }
    }

    private AiPricePredictionClientException failure(
            AiPricePredictionClientException.FailureType type,
            String message,
            RuntimeException cause) {
        return new AiPricePredictionClientException(type, message, cause);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
