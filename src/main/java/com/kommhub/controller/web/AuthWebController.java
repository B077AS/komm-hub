package com.kommhub.controller.web;

import com.kommhub.service.BetaKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final BetaKeyService betaKeyService;

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("betaEnabled", betaKeyService.isBetaEnabled());
        return "auth/register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "auth/reset-password";
    }

    @GetMapping("/verify-email")
    public String verifyEmailPage(@RequestParam(required = false, defaultValue = "") String email,
                                  Model model) {
        model.addAttribute("email", email);
        return "auth/verify-email";
    }
}
