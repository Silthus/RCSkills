package net.silthus.skills;

import io.ebean.Database;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;
import net.silthus.skills.entities.SkilledPlayer;
import net.silthus.skills.requirements.PermissionRequirement;
import net.silthus.skills.requirements.SkillRequirement;
import net.silthus.skills.skills.PermissionSkill;
import org.apache.commons.lang.NotImplementedException;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Log(topic = "sSkills")
@Getter
@Accessors(fluent = true)
public final class SkillManager {

    @Getter
    private static SkillManager instance;

    private final Map<String, Requirement.Registration<?>> requirements = new HashMap<>();
    private final Map<String, Skill.Registration<?>> skillTypes = new HashMap<>();

    private final Map<String, Skill> loadedSkills = new HashMap<>();

    private final Database database;

    SkillManager(Database database) {
        this.database = database;
        instance = this;
    }

    /**
     * Registers default requirements and skill types provided by this plugin.
     */
    void registerDefaults() {

        registerRequirement(PermissionRequirement.class, PermissionRequirement::new);
        registerRequirement(SkillRequirement.class, () -> new SkillRequirement(this));
        registerSkill(PermissionSkill.class, PermissionSkill::new);
    }

    /**
     * Recursively loads all skill configs in the given path, creates and caches new skill instances for them.
     * Each file will be loaded and passed into {@link #loadSkill(String, ConfigurationSection)}.
     *
     * @param skillConfigPath
     */
    public void loadSkills(Path skillConfigPath) {

    }

    /**
     * Registers a new requirement type with this skill manager.
     * <p>Requirement types are used to instantiate and load {@link Requirement}s as they are needed.
     * <p>Make sure your requirement is tagged with a @{@link RequirementType} annotation and has a unique type identifier.
     *
     * @param requirementClass the class of the requirement type
     * @param supplier the supplier that can create the requirement
     * @param <TRequirement> the type of the requirement
     * @return this skill manager
     */
    public <TRequirement extends Requirement> SkillManager registerRequirement(Class<TRequirement> requirementClass, Supplier<TRequirement> supplier) {

        if (!requirementClass.isAnnotationPresent(RequirementType.class)) {
            log.severe("Cannot register requirement " + requirementClass.getCanonicalName() + " without a @RequirementType annotation.");
            return this;
        }

        String type = requirementClass.getAnnotation(RequirementType.class).value().toLowerCase();
        if (requirements().containsKey(type)) {
            log.severe("Cannot register requirement: " + requirementClass.getCanonicalName()
                    + "! A requirement with the same type identifier '"
                    + type + "' is already registered: "
                    + requirements.get(type).requirementClass().getCanonicalName());
            return this;
        }

        requirements.put(type, new Requirement.Registration<>(type, requirementClass, supplier));
        log.info("registered requirement type: " + type + " [" + requirementClass.getCanonicalName() + "]");
        return this;
    }

    /**
     * Unregisters the given requirement type.
     * <p>Loaded instances of the requirement will still remain, but no new instances can be created.
     * <p>Use this method to cleanup when your plugin shuts down.
     *
     * @param requirement the class of the requirement to remove
     * @return this skill manager
     */
    public SkillManager unregisterRequirement(Class<? extends Requirement> requirement) {

        requirements.values().stream().filter(registration -> registration.requirementClass().equals(requirement))
                .forEach(registration -> requirements.remove(registration.identifier()));
        return this;
    }

    /**
     * Registers the given skill type with this skill manager.
     * <p>Make sure your skill class is annotated with @{@link SkillType} or the registration will fail.
     * <p>The provided supplier will be used to create instances of the given skill type which are then loaded
     * and applied to players.
     *
     * @param skillClass the class of the skill type to register
     * @param supplier the supplier that can create new instances of the skill
     * @param <TSkill> the type of the skill
     * @return this skill manager
     */
    public <TSkill extends Skill> SkillManager registerSkill(Class<TSkill> skillClass, Supplier<TSkill> supplier) {

        if (!skillClass.isAnnotationPresent(SkillType.class)) {
            log.severe("Cannot register skill " + skillClass.getCanonicalName() + " without a @SkillType annotation.");
            return this;
        }

        String type = skillClass.getAnnotation(SkillType.class).value().toLowerCase();
        if (skillTypes.containsKey(type)) {
            log.severe("Cannot register skill: " + skillClass.getCanonicalName()
                    + "! A skill with the same type identifier '"
                    + type + "' is already registered: "
                    + skillTypes.get(type).skillClass().getCanonicalName());
            return this;
        }

        skillTypes.put(type, new Skill.Registration<>(type, skillClass, supplier));
        log.info("registered skill type: " + type + " [" + skillClass.getCanonicalName() + "]");
        return this;
    }

    /**
     * Unregisters the given skill type from this skill manager.
     * <p>Existing instances of the skill will not be removed, but new ones can not be created.
     * <p>Use this method to cleanup your skill types when the plugin shuts down.
     *
     * @param skill the class of the skill to unregister
     * @return this skill manager
     */
    public SkillManager unregisterSkill(Class<? extends Skill> skill) {

        if (!skill.isAnnotationPresent(SkillType.class)) {
            return this;
        }
        skillTypes.remove(skill.getAnnotation(SkillType.class).value().toLowerCase());
        return this;
    }

    /**
     * Gets an existing player from the database or creates a new record from the given player.
     * <p>This method takes an {@link OfflinePlayer} for easier access to skills while players are offline.
     * However the skill can only be applied to the player if he is online. Any interaction will fail silently while offline.
     *
     * @param player the player that should be retrieved or created
     * @return a skilled player from the database
     */
    public SkilledPlayer getPlayer(OfflinePlayer player) {

        return Optional.ofNullable(database()
                .find(SkilledPlayer.class, player.getUniqueId()))
                .orElse(new SkilledPlayer(player));
    }

    /**
     * Loads and creates requirements from the provided configuration section.
     * <p>The method expects a section with unique keys and each section
     * must at least contain a valid {@code type: <requirement_type>}.
     * <p><pre>{@code
     *   requirements:
     *     foo:
     *       type: permission
     *       permissions: ...
     *     bar:
     *       type: skill
     *       skill: foobar
     *
     *   SkillManager.instance().loadRequirements(config.getConfigurationSection("requirements"));
     * }</pre>
     *
     * @param config the config section to load the requirements from
     *               can be null or empty
     * @return a list of loaded requirements
     */
    public List<Requirement> loadRequirements(ConfigurationSection config) {

        if (config == null) return new ArrayList<>();
        if (config.getKeys(false).isEmpty()) return new ArrayList<>();

        ArrayList<Requirement> requirements = new ArrayList<>();

        for (String requirementKey : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(requirementKey);
            if (section == null) continue;
            if (!section.isSet("type")) {
                log.severe("requirement section "  + config.getName() + "." + requirementKey + " is missing the requirement type.");
                continue;
            }
            String type = section.getString("type");
            if (requirements().containsKey(type)) {
                requirements.add(requirements().get(type).supplier().get().load(section));
            } else {
                log.warning("unable to find the requirement type " + type + " for " + requirementKey + " in " + config.getName() + "." + requirementKey);
            }
        }

        return requirements;
    }

    /**
     * Tries to load a skill from the given file configuration.
     * <p>The load operation will fail if the file does not exist or
     * the type key inside the config does not match any registered skill type.
     * <p>If the config does not defined an id the unique path name of the file will be used as id.
     * <p>The skill will be cached if the loading succeeds.
     * <p>An empty optional will be returned in all error cases.
     *
     * @param file the file to load the skill from
     * @return the loaded skill or an empty optional.
     */
    public Optional<Skill> loadSkill(@NonNull File file) {

        throw new NotImplementedException();
    }

    /**
     * Creates an instance of the given skill type and loads it with the given config.
     * <p>The skill can then be added and applied to players.
     * <P>Loading the skill will also cache it inside {@link #loadedSkills()} and
     * make it available from the {@link #getSkill(String)} method.
     *
     * @param type the skill type to load
     * @param config the config to load the skill with
     * @return the loaded skill or an empty optional if the skill type was not found
     */
    public Optional<Skill> loadSkill(String type, ConfigurationSection config) {

        Optional<Skill> loadedSkill = getSkillType(type)
                .map(Skill.Registration::supplier)
                .map(Supplier::get)
                .map(skill -> skill.load(config));

        loadedSkill.ifPresent(skill -> this.loadedSkills.put(skill.identifier(), skill));

        return loadedSkill;
    }

    /**
     * Tries to get skill type registration of the given identifier.
     * <p>Use the {@link Skill.Registration} to create new instances of the
     * skill and then call {@link Skill#load(ConfigurationSection)} to load it from a config.
     *
     * @param type the type identifier
     *             must not be null
     * @return the skill type registration or an empty optional
     */
    public Optional<Skill.Registration<?>> getSkillType(@NonNull String type) {

        return Optional.ofNullable(skillTypes().get(type.toLowerCase()));
    }

    /**
     * Tries to get a loaded skill with the given identifier from the cache.
     * <p>Will return an empty optional if the skill is unknown or not loaded.
     * <p>Use the {@link #getSkillType(String)} method to get the raw skill type class.
     *
     * @param identifier the identifier of the skill as defined in the config
     *                   must not be null
     * @return the skill or an empty optional
     */
    public Optional<Skill> getSkill(@NonNull String identifier) {

        return Optional.ofNullable(loadedSkills().get(identifier.toLowerCase()));
    }
}
