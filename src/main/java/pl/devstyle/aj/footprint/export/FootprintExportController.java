package pl.devstyle.aj.footprint.export;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.devstyle.aj.core.error.EntityNotFoundException;
import pl.devstyle.aj.footprint.api.exception.InvalidParametersException;
import pl.devstyle.aj.footprint.audit.FootprintAuditRepository;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/footprints/calculations")
@RequiredArgsConstructor
public class FootprintExportController {

    private static final String[] HEADER = {
            "correlation_id", "computed_at", "product_id", "timestamp_param",
            "component_path", "component_id", "kg_co2", "scope", "factor_value",
            "factor_valid_from", "factor_version_id", "warnings"
    };

    private final FootprintAuditRepository auditRepository;
    private final BreakdownCsvFlattener flattener;

    @GetMapping("/{correlationId}/export")
    public void export(@PathVariable UUID correlationId,
                       @RequestParam(name = "format", defaultValue = "csv") String format,
                       HttpServletResponse response) throws IOException {
        if (!"csv".equals(format)) {
            throw new InvalidParametersException("format", format);
        }
        var auditRow = auditRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new EntityNotFoundException("FootprintAudit", correlationId.toString()));

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8).toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"footprint-" + correlationId + ".csv\"");

        List<List<String>> rows = flattener.flatten(auditRow);

        PrintWriter writer = response.getWriter();
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(HEADER)
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            for (List<String> row : rows) {
                printer.printRecord(row);
            }
            printer.flush();
        }
    }
}
