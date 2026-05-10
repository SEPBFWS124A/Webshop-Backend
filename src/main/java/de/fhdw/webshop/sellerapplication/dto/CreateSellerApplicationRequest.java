package de.fhdw.webshop.sellerapplication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSellerApplicationRequest(
        @NotBlank(message = "Bitte einen Firmennamen angeben.")
        @Size(max = 180, message = "Der Firmenname darf maximal 180 Zeichen lang sein.")
        String companyName,

        @NotBlank(message = "Bitte einen Ansprechpartner angeben.")
        @Size(max = 180, message = "Der Ansprechpartner darf maximal 180 Zeichen lang sein.")
        String contactName,

        @NotBlank(message = "Bitte eine E-Mail-Adresse angeben.")
        @Email(message = "Bitte eine gültige E-Mail-Adresse angeben.")
        @Size(max = 255, message = "Die E-Mail-Adresse darf maximal 255 Zeichen lang sein.")
        String email,

        @Size(max = 60, message = "Die Telefonnummer darf maximal 60 Zeichen lang sein.")
        String phone,

        @Size(max = 255, message = "Die Website darf maximal 255 Zeichen lang sein.")
        String website,

        @NotBlank(message = "Bitte eine Produktkategorie auswählen.")
        @Size(max = 120, message = "Die Produktkategorie darf maximal 120 Zeichen lang sein.")
        String productCategory,

        @Size(max = 4000, message = "Die Zusatzinformationen dürfen maximal 4000 Zeichen lang sein.")
        String message
) {
}
