package pl.devstyle.aj.footprint.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.devstyle.aj.footprint.api.exception.ApplicabilityResolutionException;
import pl.devstyle.aj.footprint.api.exception.FactorVersionOverlapException;
import pl.devstyle.aj.footprint.api.exception.FootprintCalculationException;
import pl.devstyle.aj.footprint.api.exception.InvalidParametersException;
import pl.devstyle.aj.footprint.api.exception.MissingFactorException;
import pl.devstyle.aj.footprint.api.exception.MissingProductAttributeException;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice(basePackages = "pl.devstyle.aj.footprint")
@Order(Ordered.HIGHEST_PRECEDENCE)
class FootprintExceptionHandler {

    private final String problemBaseUri;

    FootprintExceptionHandler(@Value("${app.footprint.problem-base-uri}") String problemBaseUri) {
        this.problemBaseUri = problemBaseUri.endsWith("/") ? problemBaseUri : problemBaseUri + "/";
    }

    @ExceptionHandler(MissingFactorException.class)
    ResponseEntity<ProblemDetail> handleMissingFactor(MissingFactorException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex, "Missing emission factor", req);
    }

    @ExceptionHandler(MissingProductAttributeException.class)
    ResponseEntity<ProblemDetail> handleMissingProductAttribute(MissingProductAttributeException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex, "Missing product attribute", req);
    }

    @ExceptionHandler(InvalidParametersException.class)
    ResponseEntity<ProblemDetail> handleInvalidParameters(InvalidParametersException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, ex, "Invalid parameters", req);
    }

    @ExceptionHandler(ApplicabilityResolutionException.class)
    ResponseEntity<ProblemDetail> handleApplicabilityResolution(ApplicabilityResolutionException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex, "Applicability resolution failed", req);
    }

    @ExceptionHandler(FactorVersionOverlapException.class)
    ResponseEntity<ProblemDetail> handleFactorVersionOverlap(FactorVersionOverlapException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, ex, "Factor version overlap", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        InvalidParametersException wrapped = new InvalidParametersException(ex.getName(), ex.getValue());
        return problem(HttpStatus.BAD_REQUEST, wrapped, "Invalid parameters", req);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, FootprintCalculationException ex, String title, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create(problemBaseUri + kebab(ex.code())));
        pd.setTitle(title);
        // Path-only instance URI — RFC 7807 allows relative URIs and we avoid exposing scheme/host
        // (which would leak internal topology when behind a proxy that sets X-Forwarded-* headers).
        pd.setInstance(URI.create(req.getRequestURI()));
        for (Map.Entry<String, Object> e : ex.details().entrySet()) {
            pd.setProperty(e.getKey(), e.getValue());
        }
        pd.setProperty("code", ex.code());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    private static String kebab(String upperSnake) {
        return upperSnake.toLowerCase().replace('_', '-');
    }
}
