package de.raidcraft.skills.entities;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import de.raidcraft.skills.SkillsPlugin;
import de.raidcraft.skills.requirements.LevelRequirement;
import de.raidcraft.skills.requirements.PermissionRequirement;
import de.raidcraft.skills.requirements.SkillRequirement;
import org.assertj.core.groups.Tuple;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ALL")
class ConfiguredSkillTest {

    private static final String TEST_SKILL = "test";

    private ServerMock server;
    private SkillsPlugin plugin;

    @BeforeEach
    void setUp() {

        this.server = MockBukkit.mock();
        this.plugin = MockBukkit.load(SkillsPlugin.class);

        MemoryConfiguration cfg = new MemoryConfiguration();
        cfg.set("type", "permission");
        cfg.set("name", "Test Skill");
        cfg.set("restricted", true);
        cfg.set("level", 5);
        cfg.set("with.permissions", Arrays.asList("foobar", "foo"));
        plugin.getSkillManager().loadSkill(TEST_SKILL, cfg);
    }

    @AfterEach
    void tearDown() {

        MockBukkit.unmock();
    }

    @Test
    @DisplayName("should add implicit level requirement based on config")
    void shouldHaveLevelRequirement() {

        assertThat(ConfiguredSkill.findByAliasOrName(TEST_SKILL))
                .isPresent()
                .get()
                .extracting(ConfiguredSkill::requirements)
                .asList()
                .hasAtLeastOneElementOfType(LevelRequirement.class)
                .filteredOn(o -> o instanceof LevelRequirement)
                .extracting("level")
                .contains(5);
    }

    @Test
    @DisplayName("should add implicit permission requirement for alias if restricted")
    void shouldHavePermissionRequirementForAlias() {

        assertThat(ConfiguredSkill.findByAliasOrName(TEST_SKILL))
                .isPresent()
                .get()
                .extracting(ConfiguredSkill::requirements)
                .asList()
                .hasAtLeastOneElementOfType(PermissionRequirement.class)
                .filteredOn(o -> o instanceof PermissionRequirement)
                .extracting("permissions")
                .asList()
                .contains(Collections.singletonList(SkillsPlugin.SKILL_PERMISSION_PREFIX + TEST_SKILL));
    }

    @Test
    @DisplayName("should only serialize config pure values and not nested top level keys")
    void shouldSerializeConfigWithoutConfigSectionToStrings() {

        ConfiguredSkill skill = ConfiguredSkill.findByAliasOrName(TEST_SKILL).get();
        assertThat(skill.config())
                .doesNotContainKey("with")
                .containsKey("with.permissions");
    }

    @Nested
    @DisplayName("Parent -> Child")
    class ParentChildSkills {

        private static final String parent = "parent";
        private static final String child1 = "parent:child1";
        private static final String child2 = "parent:child1:child2";

        private ConfiguredSkill loadSkill() {

            return loadSkill(parent, configurationSection -> {
            });
        }

        private ConfiguredSkill loadSkill(String alias) {

            return loadSkill(alias, configurationSection -> {
            });
        }

        private ConfiguredSkill loadSkill(String alias, Consumer<ConfigurationSection> config) {

            MemoryConfiguration cfg = new MemoryConfiguration();
            cfg.set("type", "none");
            cfg.set("level", 5);
            cfg.set("skills.child1.name", child1);
            cfg.set("skills.child1.skills.child2.name", child2);
            config.accept(cfg);

            plugin.getSkillManager().loadSkill(parent, cfg);

            return getOrAssertSkill(alias);
        }

        private ConfiguredSkill getOrAssertSkill(String name) {

            Optional<ConfiguredSkill> skill = ConfiguredSkill.findByAliasOrName(name);
            assertThat(skill).isPresent();
            return skill.get();
        }

        @Test
        @DisplayName("should load child skills recursively")
        void shouldLoadChildSkillsRecursively() {

            ConfiguredSkill skill = loadSkill();
            assertThat(skill)
                    .extracting(ConfiguredSkill::isParent, s -> s.children().size())
                    .contains(true, 1);

            assertThat(skill.children())
                    .hasSize(1)
                    .extracting(
                            ConfiguredSkill::name,
                            ConfiguredSkill::isParent,
                            ConfiguredSkill::isChild,
                            s -> s.children().size(),
                            ConfiguredSkill::level
                    )
                    .contains(Tuple.tuple(
                            child1,
                            true,
                            true,
                            1,
                            5
                    ));

            assertThat(getOrAssertSkill(child2))
                    .extracting(ConfiguredSkill::type, ConfiguredSkill::isParent, ConfiguredSkill::isChild)
                    .contains("none", false, true);
        }

        @Test
        @DisplayName("should hide sub skills by default")
        void shouldHideSubSkillsByDefault() {

            assertThat(loadSkill(child1))
                    .extracting(ConfiguredSkill::hidden)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("should allow overriding hidden status")
        void shouldAllowOverridingHiddenStatus() {

            assertThat(loadSkill(child1, cfg -> {
                cfg.set("skills.child1.hidden", false);
            })).extracting(ConfiguredSkill::hidden)
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("should add skill requirement to child skills")
        void shouldAddSkillRequirementtoChildren() {

            assertThat(loadSkill(child1))
                    .extracting(ConfiguredSkill::requirements)
                    .asList()
                    .hasAtLeastOneElementOfType(SkillRequirement.class)
                    .filteredOn(o -> o instanceof SkillRequirement)
                    .extracting("skill", "hidden")
                    .contains(Tuple.tuple(ConfiguredSkill.findByAliasOrName(parent).get(), true));
        }
    }
}