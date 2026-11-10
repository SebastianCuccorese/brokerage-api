package dev.cuccorese.brokerage.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/instruments")
class InstrumentController {

    private final InstrumentRepository instruments;

    @GetMapping
    Page<InstrumentView> search(@RequestParam @NotBlank @Size(max = 100) String query, Pageable pageable) {
        String pattern = "%" + EscapeCharacter.DEFAULT.escape(query.strip()) + "%";
        return instruments.searchTradable(pattern, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }
}
