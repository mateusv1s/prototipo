package com.chatestoque;

// Classe só pra validar CPF. Não guarda nada, só tem lógica.
// O método é static porque não faz sentido criar um objeto só pra chamar uma função.
// Você chama direto: CPFValidator.validar("12345678901")
public class CPFValidator {

    public static boolean validar(String cpf) {

        // O usuário pode mandar o CPF de vários jeitos: "123.456.789-09", "123 456 789 09", tudo junto...
        // Aqui a gente joga fora tudo que não for número pra comparar só os dígitos.
        cpf = cpf.replaceAll("[^0-9]", "");

        // CPF brasileiro tem sempre 11 dígitos. Se não bater, já rejeita.
        // O "\\d{11}" significa: exatamente 11 dígitos numéricos, nada mais.
        if (!cpf.matches("\\d{11}")) {
            return false;
        }
        return true;
    }
}