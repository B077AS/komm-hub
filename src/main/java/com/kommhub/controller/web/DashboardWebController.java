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

    // -- Section fragments - the sidebar swaps these into #dash-content via htmx
    //    instead of navigating; the profile one also backs the first paint of
    //    the full page (see dashboard.html). --

    @GetMapping("/dashboard/section/profile")
    public String profileSection(Model model) {
        User user = securityUtil.getCurrentUser();
        model.addAttribute("user", userService.toDto(user));
        model.addAttribute("statusColor", statusColor(user.getStatus()));
        model.addAttribute("statusLabel", statusLabel(user.getStatus()));
        return "dashboard-fragments :: profile";
    }

    @GetMapping("/dashboard/section/beta-keys")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String betaKeysSection(Model model) {
        model.addAttribute("betaKeys", betaKeyService.listKeys());
        return "dashboard-fragments :: beta-keys";
    }

    @GetMapping("/dashboard/section/badges")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String badgesSection(Model model) {
        model.addAttribute("badges", badgeService.listBadgesForAdmin());
        return "dashboard-fragments :: badges";
    }

    // -- Admin actions - each returns the section fragment it belongs to when
    //    called via htmx (keeping the visitor on that section), and falls back
    //    to the old full-page redirect for a plain, JS-less form submit. --

    @PostMapping("/dashboard/beta-keys")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String generateBetaKeys(@RequestParam(defaultValue = "1") int count,
                                    @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                    Model model, RedirectAttributes redirectAttributes) {
        String error = null;
        try {
            betaKeyService.generateKeys(count);
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        if (hxRequest != null) {
            if (error != null) model.addAttribute("error", error);
            model.addAttribute("betaKeys", betaKeyService.listKeys());
            return "dashboard-fragments :: beta-keys";
        }
        if (error != null) redirectAttributes.addFlashAttribute("error", error);
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/beta-keys/{id}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteBetaKey(@PathVariable UUID id,
                                 @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                                 Model model, RedirectAttributes redirectAttributes) {
        String error = null;
        try {
            betaKeyService.deleteKey(id);
        } catch (IllegalStateException e) {
            error = e.getMessage();
        }
        if (hxRequest != null) {
            if (error != null) model.addAttribute("error", error);
            model.addAttribute("betaKeys", betaKeyService.listKeys());
            return "dashboard-fragments :: beta-keys";
        }
        if (error != null) redirectAttributes.addFlashAttribute("error", error);
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
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               Model model, RedirectAttributes redirectAttributes) {
        String error = null;
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
            error = e.getMessage();
        }
        if (hxRequest != null) {
            if (error != null) model.addAttribute("error", error);
            model.addAttribute("badges", badgeService.listBadgesForAdmin());
            return "dashboard-fragments :: badges";
        }
        if (error != null) redirectAttributes.addFlashAttribute("error", error);
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/badges/{id}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteBadge(@PathVariable UUID id,
                               @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                               Model model, RedirectAttributes redirectAttributes) {
        String error = null;
        try {
            badgeService.deleteBadge(id);
        } catch (IllegalStateException e) {
            error = e.getMessage();
        }
        if (hxRequest != null) {
            if (error != null) model.addAttribute("error", error);
            model.addAttribute("badges", badgeService.listBadgesForAdmin());
            return "dashboard-fragments :: badges";
        }
        if (error != null) redirectAttributes.addFlashAttribute("error", error);
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
