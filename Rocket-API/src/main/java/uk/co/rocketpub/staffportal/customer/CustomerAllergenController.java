package uk.co.rocketpub.staffportal.customer;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/allergens")
public class CustomerAllergenController {

    private final AllergenMenuService service;

    public CustomerAllergenController(AllergenMenuService service) {
        this.service = service;
    }

    @GetMapping
    public List<AllergenItemView> getMenu() {
        return service.getMenu();
    }
}
