package dev.cuccorese.brokerage.trading;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PortfolioController {

    private final PortfolioService portfolios;

    @GetMapping("/users/{userId}/portfolio")
    PortfolioView portfolio(@PathVariable Integer userId) {
        return portfolios.portfolioOf(userId);
    }
}
