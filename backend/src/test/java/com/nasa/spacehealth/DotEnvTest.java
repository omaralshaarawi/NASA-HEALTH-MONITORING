package com.nasa.spacehealth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvTest {

    @AfterEach
    void clear() {
        List.of("DOTENV_A", "DOTENV_B", "DOTENV_C", "DOTENV_D").forEach(System::clearProperty);
    }

    @Test
    void readsPlainExportedAndQuotedLines() {
        DotEnv.apply(List.of(
                "# comment",
                "",
                "DOTENV_A=plain",
                "export DOTENV_B=\"quoted value\"",
                "  DOTENV_C = 'single' ",
                "not a valid line"), Map.of());

        assertThat(System.getProperty("DOTENV_A")).isEqualTo("plain");
        assertThat(System.getProperty("DOTENV_B")).isEqualTo("quoted value");
        assertThat(System.getProperty("DOTENV_C")).isEqualTo("single");
    }

    @Test
    void realEnvironmentVariableWins() {
        DotEnv.apply(List.of("DOTENV_D=from-file"), Map.of("DOTENV_D", "from-env"));
        assertThat(System.getProperty("DOTENV_D")).isNull();
    }
}
