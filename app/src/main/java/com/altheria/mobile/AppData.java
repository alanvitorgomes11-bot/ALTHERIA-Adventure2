package com.altheria.mobile;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class AppData {
    private static Context context;
    final List<Models.Event> events=new ArrayList<>();
    final Map<String,Models.Event> eventById=new HashMap<>();
    final List<Models.Ability> elythAbilities=new ArrayList<>();
    final Map<String,Models.Ability> abilityById=new HashMap<>();
    final List<Models.Ability> armorialAbilities=new ArrayList<>();
    final List<Models.Ability> martialAbilities=new ArrayList<>();
    final List<Models.Spell> spells=new ArrayList<>();
    final Map<String,Models.Spell> spellById=new HashMap<>();
    final List<Models.Monster> monsters=new ArrayList<>();
    final Map<String,Models.Monster> monsterById=new HashMap<>();
    final List<Models.Fauna> fauna=new ArrayList<>();
    final Map<String,Models.Fauna> faunaById=new HashMap<>();
    final List<Models.Transport> transports=new ArrayList<>();
    final Map<String,Models.Transport> transportById=new HashMap<>();
    final List<Models.Renegade> renegades=new ArrayList<>();
    final Map<String,Models.Renegade> renegadeById=new HashMap<>();
    final List<Models.Item> items=new ArrayList<>();
    final Map<String,Models.Item> itemById=new HashMap<>();
    final List<Models.Npc> npcs=new ArrayList<>();
    final Map<String,Models.Npc> npcById=new HashMap<>();
    final List<Models.Route> routes=new ArrayList<>();
    final List<Models.Dungeon> dungeons=new ArrayList<>();
    final Map<String,Models.Dungeon> dungeonById=new HashMap<>();
    final List<String> natureNames=new ArrayList<>();
    final List<String> realms=new ArrayList<>();
    final Map<String,List<String>> citiesByRealm=new HashMap<>();
    final Set<String> cities=new LinkedHashSet<>();
    final Map<String,String> prerequisiteById=new HashMap<>();
    final Map<String,String> skillTreeById=new HashMap<>();
    final Map<String,String> narrativeTextById=new HashMap<>();
    final List<Models.Location> locations=new ArrayList<>();
    final List<Models.Achievement> achievements=new ArrayList<>();
    final List<Models.Prologue> prologues=new ArrayList<>();
    final Map<String,Models.Prologue> prologueById=new HashMap<>();
    final List<String> calendarDays=new ArrayList<>();
    final List<String> academyClans=new ArrayList<>();
    final List<String> academyEvents=new ArrayList<>();
    final List<String> interactions=new ArrayList<>();
    final List<String> grimoires=new ArrayList<>();

    static void init(Context c){ context=c.getApplicationContext(); }
    AppData() throws IOException { if(context==null) throw new IllegalStateException("AppData.init(Context) não foi chamado."); loadAll(); validate(); }

    private static List<String> lines(String resource)throws IOException{
        try(InputStream in=context.getAssets().open("resources/"+resource)){
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().filter(s->!s.isBlank()).toList();
        }
    }
    private static String[] split(String line){return line.split("\\t",-1);}
    private void loadAll()throws IOException{
        for(String l:lines("world/natures.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);natureNames.add(p[1]);}
        for(String l:lines("world/world.tsv")){if(l.startsWith("kind\t"))continue;String[]p=split(l);if(p[0].equals("realm"))realms.add(p[1]);if(p[0].equals("city")){citiesByRealm.computeIfAbsent(p[2],k->new ArrayList<>()).add(p[1]);cities.add(p[1]);}}
        for(String l:lines("world/routes.tsv")){if(l.startsWith("from\t"))continue;String[]p=split(l);routes.add(new Models.Route(p[0],p[1],Integer.parseInt(p[2]),Integer.parseInt(p[3]),Integer.parseInt(p[4]),p[5]));}
        for(String l:lines("world/transport.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Transport t=new Models.Transport(p[0],p[1],p[2],p[3],Double.parseDouble(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7]),p[8]);transports.add(t);transportById.put(t.id(),t);}
        for(String l:lines("world/dungeons.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Dungeon d=new Models.Dungeon(p[0],p[1],p[2],p[3],Integer.parseInt(p[4]),Integer.parseInt(p[5]),Integer.parseInt(p[6]),Integer.parseInt(p[7]),p[8]);dungeons.add(d);dungeonById.put(d.id(),d);}
        loadNarrativeTexts("events/narrative_text.tsv");
        loadEvents("events/events.tsv"); loadEvents("events/expansion.tsv"); loadEvents("events/career_and_world_expansion.tsv");
        for(String l:lines("abilities/elyth_abilities.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Ability a=new Models.Ability(p[0],p[2],p[3],p[4],Integer.parseInt(p[5]),p[6]);elythAbilities.add(a);abilityById.put(a.id(),a);}
        for(String l:lines("abilities/vellum_armorial.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);armorialAbilities.add(new Models.Ability(p[0],"VELLUM",p[3],p[1],Integer.parseInt(p[2]),p[4]));}
        for(String l:lines("abilities/vellum_martial.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);martialAbilities.add(new Models.Ability(p[0],"VELLUM",p[5],p[2],Integer.parseInt(p[4]),p[6]));}
        for(String l:lines("magic/spells.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Spell s=new Models.Spell(p[0],p[1],p[2],Integer.parseInt(p[3]),Integer.parseInt(p[4]),p[5]);spells.add(s);spellById.put(s.id(),s);}
        for(String l:lines("abilities/prerequisites.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);prerequisiteById.put(p[0],p[1]);skillTreeById.put(p[0],p[2]);}
        loadMonsters("monsters/monsters.tsv"); loadMonsters("monsters/expansion.tsv");
        for(String l:lines("fauna/fauna.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Fauna f=new Models.Fauna(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],Integer.parseInt(p[8]),p[9]);fauna.add(f);faunaById.put(f.id(),f);}
        for(String l:lines("renegades/renegades.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Renegade r=new Models.Renegade(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],Integer.parseInt(p[8]),Integer.parseInt(p[9]),Integer.parseInt(p[10]),Integer.parseInt(p[11]),Integer.parseInt(p[12]),p[13]);renegades.add(r);renegadeById.put(r.id(),r);}
        for(String l:lines("items/items.tsv")){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Item i=new Models.Item(p[0],p[1],p[2],p[3],safeInt(p[4],0),safeInt(p[5],0),safeInt(p[6],0),safeInt(p[7],0),safeInt(p[8],0),Boolean.parseBoolean(p[9]),p.length>10?p[10]:"",p.length>11?safeInt(p[11],0):0,p.length>12?safeInt(p[12],0):0);items.add(i);itemById.put(i.id(),i);}
        loadNpcs("relationships/npcs.tsv"); loadNpcs("relationships/npcs_expansion.tsv");
        loadLocations("world/locations.tsv");
        loadSimple("world/calendar.tsv",calendarDays);
        loadSimple("academy/clans.tsv",academyClans); loadSimple("academy/events.tsv",academyEvents);
        loadSimple("relationships/interactions.tsv",interactions);
        loadSimple("magic/grimoires.tsv",grimoires);
        loadAchievements("progression/achievements.tsv"); loadPrologues("progression/epilogues.tsv");
    }
    private void loadEvents(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Choice c1=new Models.Choice(p[11],p[12],p[13],p[14],p[15],p[16],p[17]);Models.Choice c2=new Models.Choice(p[18],p[19],p[20],p[21],p[22],p[23],p[24]);String narrative=narrativeTextById.getOrDefault(p[0],p[10]); Models.Event e=new Models.Event(p[0],p[1],p[2],p[3],p[4],p[5],Integer.parseInt(p[6]),Integer.parseInt(p[7]),Integer.parseInt(p[8]),p[9],narrative,c1,c2,p[25],p[26],Integer.parseInt(p[27]));events.add(e);eventById.put(e.id(),e);}}
    private void loadMonsters(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Monster m=new Models.Monster(p[0],p[1],p[2],Integer.parseInt(p[3]),p[4],p[5],Integer.parseInt(p[6]),Integer.parseInt(p[7]),Integer.parseInt(p[8]),Integer.parseInt(p[9]),Integer.parseInt(p[10]),p[11],p[12]);monsters.add(m);monsterById.put(m.id(),m);}}
    private void loadNpcs(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);int age=p.length>7?safeInt(p[7],30):30;String occ=p.length>8?p[8]:p[5];String status=p.length>9?p[9]:"ACTIVE";String gender=p.length>10?p[10]:(p[0].hashCode()%2==0?"MASCULINO":"FEMININO");int ay=p.length>11?safeInt(p[11],0):0;String ac=p.length>12?p[12]:"";String clan=p.length>13?p[13]:"";Models.Npc n=new Models.Npc(p[0],p[1],p[2],p[3],Integer.parseInt(p[4]),p[5],p[6],age,occ,status,gender,ay,ac,clan);npcs.add(n);npcById.put(n.id(),n);}}
    private void loadNarrativeTexts(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);if(p.length>=2)narrativeTextById.put(p[0],p[1]);}}
    private void loadSimple(String resource,List<String>out)throws IOException{for(String l:lines(resource)){if(!l.startsWith("id\t") && !l.startsWith("month\t"))out.add(l);}}
    private void loadLocations(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);locations.add(new Models.Location(p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9]));}}
    private void loadAchievements(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);achievements.add(new Models.Achievement(p[0],p[1],p[2],p[3],safeInt(p[4],0),p[5].equalsIgnoreCase("YES")));}}
    private void loadPrologues(String resource)throws IOException{for(String l:lines(resource)){if(l.startsWith("id\t"))continue;String[]p=split(l);Models.Prologue e=new Models.Prologue(p[0],p[1],p[2],p[3],p[4],p[5]);prologues.add(e);prologueById.put(e.id(),e);}}
    private int safeInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private void validate(){if(items.size()!=4000||events.size()!=7880||elythAbilities.size()!=1000||spells.size()!=1000||natureNames.size()!=50||monsters.size()!=3600||fauna.size()!=240||transports.size()!=60||renegades.size()!=1200||npcs.size()<4280||locations.size()<1000||achievements.size()!=3000||prologues.size()!=1200)throw new IllegalStateException("Conteúdo Altheria incompleto no pacote Android.");}
}
