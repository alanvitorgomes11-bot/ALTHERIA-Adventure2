package com.altheria.mobile;

import java.util.*;

final class Models {
    private Models() {}

    enum LifePath { ADVENTURER, VALARYN, INDEPENDENT }
    enum EventMode { STORY, FREE }

    static final class Player {
        String name = "Aventureiro";
        LifePath lifePath = LifePath.ADVENTURER;
        String realm = "Aeloria";
        String city = "Valdora";
        String profession = "Aventureiro";
        String background = "Sonhador";
        int age = 18;
        int level = 1;
        int xp = 0;
        int hp = 30, maxHp = 30;
        int gold = 40;
        int reputation = 0;
        int fatigue = 0;
        // Survival needs: 0 = depleted, 100 = fully satisfied.
        int hunger = 85, thirst = 85;
        int day = 1;
        int academyDay = 0;
        String currentEventId = "";
        String nature = "";
        int vellumArmorial = 0;
        int vellumMartial = 0;
        int mana = 0, maxMana = 0;
        int skillPoints = 0;
        int guildRank = 0;
        int guildRep = 0;
        int arenaRating = 1000, arenaWins = 0, arenaLosses = 0;
        String activeDungeon = "";
        int dungeonFloor = 0;

        // Social / life simulation.
        final Map<String,String> relationshipStages = new LinkedHashMap<>();
        final Map<String,Integer> npcSearches = new LinkedHashMap<>();
        final Set<String> childrenNames = new LinkedHashSet<>();
        String spouseId = "";
        int childrenCount = 0;

        // Transport ownership / active mount.
        final Set<String> ownedTransports = new LinkedHashSet<>();
        String activeTransportId = "";

        // Hunt board persistence.
        String activeHuntType = "";     // MONSTER / RENEGADE
        String activeHuntTargetId = "";
        String activeHuntMode = "";     // CAPTURE / ELIMINATE
        int huntIntel = 0;
        int huntIntelRequired = 0;

        final Map<String,Integer> stats = new LinkedHashMap<>();
        final Set<String> flags = new LinkedHashSet<>();
        final Set<String> seenEvents = new LinkedHashSet<>();
        final Map<String,Integer> eventHistory = new LinkedHashMap<>();
        final Set<String> abilities = new LinkedHashSet<>();
        final Set<String> spells = new LinkedHashSet<>();
        final Map<String,Integer> relations = new LinkedHashMap<>();
        final Map<String,Integer> inventory = new LinkedHashMap<>();
        final Map<String,String> equipment = new LinkedHashMap<>();
        final Set<String> discoveredCities = new LinkedHashSet<>();
        final Set<String> completedQuests = new LinkedHashSet<>();
        final Map<String,Integer> cityStability = new LinkedHashMap<>();
        final Map<String,Integer> cityEconomy = new LinkedHashMap<>();
        final Map<String,Integer> cityThreat = new LinkedHashMap<>();
        final Set<String> achievements = new LinkedHashSet<>();
        final Set<String> partyMembers = new LinkedHashSet<>();
        final List<String> prologue = new ArrayList<>();

        Player() {
            stats.put("FOR",5); stats.put("VIG",5); stats.put("AGI",5); stats.put("PER",5);
            stats.put("INT",5); stats.put("VON",5); stats.put("PRE",5); stats.put("TEC",5);
        }

        int stat(String key) { return stats.getOrDefault(key,0); }
        void statAdd(String key, int delta) { stats.put(key, Math.max(0, stat(key)+delta)); }
        int itemCount(String id) { return inventory.getOrDefault(id,0); }
        void addItem(String id, int amount) { if(amount<=0)return; inventory.merge(id, amount, Integer::sum); }
        boolean removeItem(String id, int amount) {
            if (amount<=0) return true;
            int n=itemCount(id); if(n<amount)return false;
            if(n==amount) inventory.remove(id); else inventory.put(id,n-amount);
            return true;
        }
        int relation(String npcId) { return relations.getOrDefault(npcId,0); }
        void addFlag(String flag){if(flag!=null&&!flag.isBlank())flags.add(flag);}
        boolean hasFlag(String flag){return flags.contains(flag);}
        void relationAdd(String npcId,int delta){ relations.put(npcId, Math.max(-100, Math.min(100, relation(npcId)+delta))); }
        boolean hasAbility(String id){return abilities.contains(id);}
        boolean hasSpell(String id){return spells.contains(id);}
        int cityValue(Map<String,Integer> map,String city) { return map.getOrDefault(city,0); }
        void cityAdd(Map<String,Integer> map,String city,int delta) { map.put(city,Math.max(-100,Math.min(100,map.getOrDefault(city,0)+delta))); }
        int relationship(String npcId) { return relationshipStages.containsKey(npcId) ? switch(relationshipStages.get(npcId)) {
            case "AMIGO" -> 10; case "PROXIMO" -> 25; case "ROMANCE" -> 45; case "PARCEIRO" -> 60; case "CASADO" -> 80; default -> relation(npcId);
        } : relation(npcId); }
        void setRelationshipStage(String npcId,String stage){relationshipStages.put(npcId,stage);}
        String relationshipStage(String npcId){return relationshipStages.getOrDefault(npcId, relation(npcId)>=10?"AMIGO":"CONHECIDO");}
        boolean adult(){return age>=18;}

        int academyYear() {
            if (lifePath != LifePath.VALARYN) return 0;
            if (academyDay < 365) return 1;
            if (academyDay < 730) return 2;
            if (academyDay < 1095) return 3;
            return 4;
        }
        int academySemester() {
            if (lifePath != LifePath.VALARYN || academyDay >= 1095) return 0;
            if (academyDay < 183) return 1;
            if (academyDay < 365) return 2;
            if (academyDay < 548) return 1;
            if (academyDay < 730) return 2;
            if (academyDay < 913) return 1;
            return 2;
        }
        String academyStage() {
            if (lifePath != LifePath.VALARYN) return "—";
            if (academyDay < 183) return "1º ano — Calouro • 1º semestre";
            if (academyDay < 365) return "1º ano — Calouro • 2º semestre";
            if (academyDay < 548) return "2º ano — Aprendiz de Valaryn • 1º semestre";
            if (academyDay < 730) return "2º ano — Aprendiz de Valaryn • 2º semestre";
            if (academyDay < 913) return "3º ano — Aspirante a Valaryn • 1º semestre";
            if (academyDay < 1095) return "3º ano — Aspirante a Valaryn • 2º semestre";
            return "Formado — " + rankLabel();
        }
        String rankLabel() {
            if (lifePath != LifePath.VALARYN) return "Independente";
            if (academyDay < 365) return "Calouro";
            if (academyDay < 730) return "Aprendiz de Valaryn";
            if (academyDay < 1095) return "Aspirante a Valaryn";
            if (academyDay < 1460) return "Valaryn Cadete (F)";
            if (academyDay < 2190) return "Patrulheiro (E)";
            if (academyDay < 2920) return "Vanguarda (D)";
            if (academyDay < 3650) return "Guardião (C)";
            if (academyDay < 4380) return "Tático (B)";
            if (academyDay < 5110) return "Arconte (A)";
            if (academyDay < 5840) return "Mestre (S)";
            return "Grão-Mestre (Z)";
        }
        int equipmentBonus(String kind, AppData data) {
            int total=0;
            for(String id:equipment.values()){
                Item i=data.itemById.get(id); if(i==null)continue;
                total += kind.equals("ATTACK")?i.attack():kind.equals("DEFENSE")?i.defense():0;
            }
            return total;
        }
        void addXp(int amount) {
            if(amount<=0)return;
            xp += amount;
            while (xp >= level * 30) {
                xp -= level * 30; level++; skillPoints++; maxHp += 3; hp = maxHp; statAdd("VIG",1);
            }
        }
    }

    record Choice(String text, String requirement, String next, String successEffect,
                  String failNext, String failEffect, String combatMonster) {}
    record Event(String id, String category, String rarity, String lifePath, String realm,
                 String location, int minLevel, int minDay, int weight, String title, String text,
                 Choice choice1, Choice choice2, String tags, String requirements, int cooldown) {}
    record Ability(String id, String nature, String name, String category, int tier, String description) {}
    record Spell(String id, String school, String name, int tier, int mana, String description) {}
    record Monster(String id, String name, String realm, int threatClass, String energy, String behavior,
                   int hp, int attack, int defense, int xp, int gold, String habitat, String weakness) {}
    record Item(String id, String name, String category, String slot, int price, int heal, int mana,
                int attack, int defense, boolean consumable, String description, int hunger, int thirst) {}
    record Npc(String id, String name, String realm, String city, int startingRelation, String role, String description,
               int age, String occupation, String status, String gender, int academyYear, String academyClass, String clan) {}
    record Route(String from, String to, int days, int cost, int risk, String status) {}
    record Dungeon(String id, String name, String realm, String city, int maxFloor, int minThreat, int maxThreat, int entryCost, String status) {}
    record Location(String id,String realm,String city,String kind,String name,String description,String access,String hours,String minRank,String rarity) {}
    record Achievement(String id,String category,String name,String description,int points,boolean hidden) {}
    record Prologue(String id,String type,String title,String trigger,String text,String legacy) {}
    record Renegade(String id, String name, String rank, String realm, String approxLocation, String hideout,
                    String primaryPath, String powerSummary, int threat, int bounty, int captureReward,
                    int eliminationReward, int intelRequired, String status) {}
    record Fauna(String id, String name, String category, String realm, String habitat, String behavior,
                 String domestication, String transport, int threat, String description) {}
    record Transport(String id, String name, String type, String realm, double speed, int cost,
                     int capacity, int riskModifier, String description) {}
}
