package com.kommhub.controller.web;

import com.kommhub.model.db.User;
import com.kommhub.model.dto.request.BadgeCreateRequest;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.BadgeIconService;
import com.kommhub.service.BadgeService;
import com.kommhub.service.BetaKeyService;
import com.kommhub.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DashboardWebController {

    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final BetaKeyService betaKeyService;
    private final BadgeService badgeService;
    private final BadgeIconService badgeIconService;

    @GetMapping("/dashboard")
    public String dashboardPage(Model model) {
        User user = securityUtil.getCurrentUser();
        model.addAttribute("user", userService.toDto(user));
        model.addAttribute("statusColor", statusColor(user.getStatus()));
        model.addAttribute("statusLabel", statusLabel(user.getStatus()));

        boolean isAdmin = user.getRole() == User.Role.SUPER_ADMIN;
        model.addAttribute("isAdmin", isAdmin);
        if (isAdmin) {
            model.addAttribute("betaKeys", betaKeyService.listKeys());
            model.addAttribute("badges", badgeService.listBadgesForAdmin());
        }
        return "dashboard";
    }

    @PostMapping("/dashboard/beta-keys")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String generateBetaKeys(@RequestParam(defaultValue = "1") int count, RedirectAttributes redirectAttributes) {
        try {
            betaKeyService.generateKeys(count);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/beta-keys/{id}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteBetaKey(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            betaKeyService.deleteKey(id);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/badges")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String createBadge(@RequestParam String name,
                               @RequestParam(required = false) String description,
                               @RequestParam String icon,
                               @RequestParam String color,
                               @RequestParam(required = false) Integer maxUses,
                               @RequestParam(required = false) String expiresAt,
                               RedirectAttributes redirectAttributes) {
        try {
            badgeService.createBadge(BadgeCreateRequest.builder()
                    .name(name)
                    .description(description)
                    .icon(icon)
                    .color(color)
                    .maxUses(maxUses)
                    .expiresAt(expiresAt != null && !expiresAt.isBlank()
                            ? LocalDate.parse(expiresAt).atTime(23, 59, 59)
                            : null)
                    .build());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/badges/{id}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteBadge(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            badgeService.deleteBadge(id);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard/badges/icons")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public Object iconCatalog() {
        return badgeIconService.getCatalog();
    }

    private static String statusColor(User.UserStatus status) {
        return switch (status) {
            case ONLINE -> "#3fb950";
            case AWAY -> "#f0a830";
            case DO_NOT_DISTURB -> "#ef4444";
            default -> "#6e7681";
        };
    }

    private static String statusLabel(User.UserStatus status) {
        return switch (status) {
            case ONLINE -> "Online";
            case AWAY -> "Away";
            case DO_NOT_DISTURB -> "Do Not Disturb";
            case INVISIBLE -> "Invisible";
            default -> "Offline";
        };
    }
}
