package dev.cuccorese.brokerage.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instruments")
class InstrumentController {

    private final InstrumentRepository instruments;

    InstrumentController(InstrumentRepository instruments) {
        this.instruments = instruments;
    }

    @GetMapping
    List<InstrumentView> search(@RequestParam @NotBlank @Size(max = 100) String query) {
        String pattern = "%" + EscapeCharacter.DEFAULT.escape(query.strip()) + "%";
        return instruments.searchTradable(pattern);
    }
}
