package com.heness.project.account.api;

import jakarta.validation.constraints.NotNull;

record UpdateAccountRequest(@NotNull String preferredLanguage) {
}
