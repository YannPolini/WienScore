package dev.polini.wienscore.assessment;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class AssessmentController {
    private final AddressLookup lookup;
    private final AssessmentService assessments;
    record Request(String address) {}

    AssessmentController(AddressLookup lookup, AssessmentService assessments) { this.lookup = lookup; this.assessments = assessments; }

    @PostMapping("/addresses/resolve")
    AddressLookup.Result resolve(@RequestBody Request request) { return lookup.resolve(request.address()); }

    @PostMapping("/assessments")
    AssessmentService.Assessment assess(@RequestBody Request request) { return assessments.assess(request.address()); }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> invalid(Exception ex) {
        String message = ex instanceof HttpMessageNotReadableException
                ? "Send a JSON object containing an address." : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("status", "INVALID_INPUT", "message", message));
    }
}
