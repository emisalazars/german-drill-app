#!/usr/bin/env python3
"""Generates the bundled seed decks (app/src/main/assets/seed/*.json) from the tutoring diagnostic.
Edit the lists below and re-run: python3 tools/make_seed.py"""
import json, os

SRC = "seed-2026-10-12"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "seed")
cards = []

def add(id, type, prompt, answers, tags, explanation=None, hint=None, instruction=None, example=None,
        contrast=None, diag=None):
    c = {"id": id, "type": type, "prompt": prompt,
         "answers": answers if isinstance(answers, list) else [answers], "tags": tags, "source": SRC}
    for k, v in [("explanation", explanation), ("hint", hint), ("instruction", instruction),
                 ("example", example), ("contrast", contrast)]:
        if v: c[k] = v
    if diag: c["diagnostic"] = {"question": diag[0], "answers": diag[1]}
    cards.append(c)

AKK = ("Kasus? (Akk/Dat)", ["Akk", "Akkusativ"])
DAT = ("Kasus? (Akk/Dat)", ["Dat", "Dativ"])
NOM = ("Kasus? (Nom/Akk/Dat/Gen)", ["Nom", "Nominativ"])
GEN = ("Kasus? (Nom/Akk/Dat/Gen)", ["Gen", "Genitiv"])
AKK4 = ("Kasus? (Nom/Akk/Dat/Gen)", ["Akk", "Akkusativ"])
DAT4 = ("Kasus? (Nom/Akk/Dat/Gen)", ["Dat", "Dativ"])
WOHIN = ("Wo oder wohin?", ["wohin"])
WO = ("Wo oder wohin?", ["wo"])
VERB_RULE = "Akk (wen/was?): besuchen, fragen, suchen, sehen, lesen, brauchen, treffen, anrufen, vermeiden. Dat (wem?): helfen, danken, antworten, gehören, gefallen, folgen, gratulieren."

# ---- akk-dat-verb-case [10] -------------------------------------------------
t = ["akk-dat-verb-case"]
add("avc-01", "fill_blank", "Am Wochenende besuche ich ___ Onkel in Bern. (mein)", "meinen", t + ["akk-mask-endings"],
    "besuchen + Akkusativ (wen?). Onkel ist maskulin → meinen.", VERB_RULE, diag=AKK, contrast="besuchen-helfen")
add("avc-02", "fill_blank", "Kannst du ___ Kollegin helfen? (die)", "der", t,
    "helfen + Dativ (wem?). Femininum Dativ → der.", VERB_RULE, diag=DAT, contrast="besuchen-helfen")
add("avc-03", "fill_blank", "Ich danke ___ Professor für das Feedback. (der)", "dem", t,
    "danken + Dativ (wem?).", VERB_RULE, diag=DAT)
add("avc-04", "fill_blank", "Wir treffen ___ Betreuer morgen um zehn. (unser)", "unseren", t + ["akk-mask-endings"],
    "treffen + Akkusativ. unser → unseren (mask. Akk).", VERB_RULE, diag=AKK)
add("avc-05", "fill_blank", "Das Fahrrad gehört ___ Bruder. (mein)", "meinem", t,
    "gehören + Dativ (wem?).", VERB_RULE, diag=DAT)
add("avc-06", "fill_blank", "Ich rufe ___ Vermieter heute Abend an. (der)", "den", t + ["akk-mask-endings"],
    "anrufen + Akkusativ (wen?), nicht Dativ.", VERB_RULE, diag=AKK)
add("avc-07", "fill_blank", "Bitte antworte ___ Kunden bis Freitag. (der, Singular)", "dem", t,
    "antworten + Dativ. Kunde ist n-Deklination: dem Kunden.", VERB_RULE, diag=DAT)
add("avc-08", "fill_blank", "Wir müssen ___ Stau auf der A1 vermeiden. (der)", "den", t + ["akk-mask-endings"],
    "vermeiden + Akkusativ.", VERB_RULE, diag=AKK)
add("avc-09", "fill_blank", "Der Vortrag hat ___ Publikum gut gefallen. (das)", "dem", t,
    "gefallen + Dativ: Etwas gefällt jemandem.", VERB_RULE, diag=DAT)
add("avc-10", "fill_blank", "Ich frage ___ Assistenten nach dem Termin. (der)", "den", t + ["akk-mask-endings"],
    "fragen + Akkusativ (wen?), auch wenn es auf Spanisch 'preguntar a alguien' heißt.", VERB_RULE, diag=AKK)
add("avc-11", "fill_blank", "Wir gratulieren ___ Team zum Erfolg. (unser)", "unserem", t,
    "gratulieren + Dativ.", VERB_RULE, diag=DAT)
add("avc-12", "fill_blank", "Ich suche ___ Schlüssel schon seit einer Stunde. (mein)", "meinen", t + ["akk-mask-endings"],
    "suchen + Akkusativ.", VERB_RULE, diag=AKK)
add("avc-13", "error_correction", "Gestern habe ich meinem Freund im Krankenhaus besucht.",
    "Gestern habe ich meinen Freund im Krankenhaus besucht.", t + ["akk-mask-endings"],
    "besuchen + Akkusativ: meinen Freund. (Dein häufigster Rückfall!)", instruction="Finde und korrigiere den Fehler.")
add("avc-14", "error_correction", "Ich habe den Professor für seine Hilfe gedankt.",
    "Ich habe dem Professor für seine Hilfe gedankt.", t, "danken + Dativ: dem Professor.",
    instruction="Finde und korrigiere den Fehler.")
add("avc-15", "speaking", "Sag: \"Yesterday I visited my supervisor (m).\"",
    ["Gestern habe ich meinen Betreuer besucht.", "Ich habe gestern meinen Betreuer besucht."], t + ["akk-mask-endings"],
    "besuchen + Akk → meinen Betreuer.")

# ---- akk-mask-endings [10] --------------------------------------------------
t = ["akk-mask-endings"]
add("akm-01", "fill_blank", "Ich habe ___ Plan für das Semester. (ein)", "einen", t, "haben + Akk; der Plan → einen Plan.", diag=AKK4)
add("akm-02", "fill_blank", "In diesem Dorf gibt es ___ Bahnhof. (kein)", "keinen", t, "es gibt + Akkusativ; der Bahnhof → keinen.", diag=AKK4)
add("akm-03", "fill_blank", "Ich kenne ___ Ingenieur nicht. (dieser)", "diesen", t, "kennen + Akk; maskulin → diesen.", diag=AKK4)
add("akm-04", "fill_blank", "Sie fährt ___ Tag mit dem Zug nach Zürich. (jeder)", "jeden", t, "Zeitangabe im Akkusativ: jeden Tag.", diag=AKK4)
add("akm-05", "fill_blank", "Wir brauchen ___ zweiten Kreisverkehr. (ein)", "einen", t, "brauchen + Akk; der Kreisverkehr → einen.", diag=AKK4)
add("akm-06", "fill_blank", "Ich lese ___ Bericht heute Abend. (dieser)", "diesen", t,
    "der Bericht → diesen (mask. Akk).", contrast="diesen-diese", diag=AKK4)
add("akm-07", "fill_blank", "Ich lese ___ E-Mail heute Abend. (dieser)", "diese", t + ["noun-gender"],
    "die E-Mail ist feminin → diese. 'diesen' nur für maskulin!", contrast="diesen-diese", diag=AKK4)
add("akm-08", "fill_blank", "Ich sehe ___ Mann mit dem Hund. (der)", "den", t, "Akkusativ maskulin = den (nicht dem).", diag=AKK4)
add("akm-09", "error_correction", "Ich habe ein Termin beim Zahnarzt.", "Ich habe einen Termin beim Zahnarzt.", t,
    "der Termin → einen Termin.", instruction="Finde und korrigiere den Fehler.")
add("akm-10", "error_correction", "Wir haben noch kein Plan B.", "Wir haben noch keinen Plan B.", t,
    "der Plan → keinen Plan.", instruction="Finde und korrigiere den Fehler.")
add("akm-11", "error_correction", "Kennst du unser neuer Nachbar?", "Kennst du unseren neuen Nachbarn?", t + ["adjective-endings"],
    "Akk mask.: unseren neuen Nachbarn (Nachbar: n-Deklination).", instruction="Finde und korrigiere die Fehler.")

# ---- weil-subject-position [9] / verb-final [9] -----------------------------
t = ["weil-subject-position"]
add("ws-01", "transform", "Ich trainiere jeden Tag. Ich laufe nächsten Monat einen Marathon.",
    "Ich trainiere jeden Tag, weil ich nächsten Monat einen Marathon laufe.", t + ["verb-final"],
    "weil + Subjekt sofort, dann Zeitangabe, Verb ans Ende.", instruction="Verbinde mit weil.")
add("ws-02", "transform", "Ich bleibe heute zu Hause. Ich bin müde.", "Ich bleibe heute zu Hause, weil ich müde bin.",
    t + ["verb-final"], instruction="Verbinde mit weil.")
add("ws-03", "error_correction", "Ich lerne Deutsch, weil nächstes Jahr ich in Zürich arbeite.",
    "Ich lerne Deutsch, weil ich nächstes Jahr in Zürich arbeite.", t, "Subjekt direkt nach weil, dann die Zeitangabe.",
    instruction="Finde und korrigiere den Fehler.")
add("ws-04", "error_correction", "Er sagt, dass morgen er keine Zeit hat.", "Er sagt, dass er morgen keine Zeit hat.", t,
    "Subjekt direkt nach dass.", instruction="Finde und korrigiere den Fehler.")
add("ws-05", "transform", "Ich glaube: Das Projekt ist machbar.", "Ich glaube, dass das Projekt machbar ist.", t + ["verb-final"],
    instruction="Mit dass.")
add("ws-06", "speaking", "Sag mit weil: \"Ich komme später. Der Zug hat Verspätung.\"",
    "Ich komme später, weil der Zug Verspätung hat.", t + ["verb-final"])
t = ["verb-final"]
add("vf-01", "transform", "Ich bin gestresst. Ich habe angefangen, ein Masterarbeitsthema zu suchen.",
    "Ich bin gestresst, weil ich angefangen habe, ein Masterarbeitsthema zu suchen.", t + ["zu-infinitive"],
    "Das konjugierte Verb (habe) steht am Ende des weil-Satzes; der zu-Infinitiv folgt danach.", instruction="Verbinde mit weil.")
add("vf-02", "transform", "Der Bericht ist spät. Wir konnten die Daten nicht rechtzeitig bekommen.",
    "Der Bericht ist spät, weil wir die Daten nicht rechtzeitig bekommen konnten.", t, instruction="Verbinde mit weil.")
add("vf-03", "transform", "Ich weiß nicht: Wird die Brücke bis 2027 fertig sein?",
    "Ich weiß nicht, ob die Brücke bis 2027 fertig sein wird.", t, "Indirekte Ja/Nein-Frage mit ob; konjugiertes Verb am Ende.",
    instruction="Mit ob.")
add("vf-04", "error_correction", "Sie hat gesagt, dass sie hat den Bericht geschrieben.",
    "Sie hat gesagt, dass sie den Bericht geschrieben hat.", t, instruction="Finde und korrigiere den Fehler.")

# ---- v2-main-clause [9] -----------------------------------------------------
t = ["v2-main-clause"]
add("v2-01", "error_correction", "Diese Woche vielleicht gehe ich ins Kino.", "Diese Woche gehe ich vielleicht ins Kino.", t,
    "Genau EIN Element vor dem Verb.", instruction="Finde und korrigiere den Fehler.")
add("v2-02", "error_correction", "Morgen ich fahre nach Bern.", "Morgen fahre ich nach Bern.", t,
    instruction="Finde und korrigiere den Fehler.")
add("v2-03", "error_correction", "Leider ich kann nicht kommen.", "Leider kann ich nicht kommen.", t,
    instruction="Finde und korrigiere den Fehler.")
add("v2-04", "transform", "Ich möchte nach dem Studium in der Schweiz arbeiten.",
    "Nach dem Studium möchte ich in der Schweiz arbeiten.", t, instruction="Beginne mit \"Nach dem Studium\".")
add("v2-05", "transform", "Ich habe gestern den ganzen Tag an der Masterarbeit gearbeitet.",
    "Gestern habe ich den ganzen Tag an der Masterarbeit gearbeitet.", t, instruction="Beginne mit \"Gestern\".")
add("v2-06", "transform", "Er sagt, dass er morgen keine Zeit hat.",
    ["Er sagt, er hat morgen keine Zeit.", "Er sagt, er habe morgen keine Zeit."], t,
    "Ohne dass: Hauptsatz-Wortstellung (V2).", instruction="Ohne dass.")

# ---- wuerde-vs-wurde [9] ----------------------------------------------------
t = ["wuerde-vs-wurde"]
add("ww-01", "fill_blank", "Wenn ich mehr Zeit hätte, ___ ich öfter laufen. (werden)", "würde", t + ["spelling"],
    "Konjunktiv II (irreal) → würde.", contrast="wurde-wuerde")
add("ww-02", "fill_blank", "Die Brücke ___ 1990 gebaut. (werden)", "wurde", t + ["passive"],
    "Passiv Präteritum → wurde.", contrast="wurde-wuerde")
add("ww-03", "fill_blank", "___ Sie mir bitte kurz helfen? (werden, höflich)", "Würden", t, "Höfliche Bitte: Konjunktiv II.")
add("ww-04", "error_correction", "Ich wurde gern mehr reisen.", "Ich würde gern mehr reisen.", t,
    instruction="Finde und korrigiere den Fehler.")
add("ww-05", "error_correction", "Das Gebäude würde 2015 eröffnet.", "Das Gebäude wurde 2015 eröffnet.", t + ["passive"],
    instruction="Finde und korrigiere den Fehler.")
add("ww-06", "speaking", "Sag: \"If I had time, I would run a marathon.\"",
    "Wenn ich Zeit hätte, würde ich einen Marathon laufen.", t + ["akk-mask-endings"])

# ---- adjective-endings [8] --------------------------------------------------
t = ["adjective-endings"]
add("adj-01", "fill_blank", "Ich habe einen ___ Vorschlag. (neu)", "neuen", t, "ein-Wort, Akk mask. → -en.", diag=AKK4)
add("adj-02", "fill_blank", "Mit dieser ___ Lösung sind alle zufrieden. (einfach)", "einfachen", t, "Dativ → immer -en.", diag=DAT4)
add("adj-03", "fill_blank", "Das ist ein ___ Projekt. (spannend)", "spannendes", t + ["noun-gender"],
    "ein + Neutrum Nom → Adjektiv zeigt das Genus: -es.", diag=NOM)
add("adj-04", "fill_blank", "Wir wohnen in einer ___ Wohnung. (klein)", "kleinen", t, "Dativ → -en.", diag=DAT4)
add("adj-05", "fill_blank", "Jeder ___ Student bekommt einen Platz. (neu)", "neue", t, "der-Wort im Nom → -e.", diag=NOM)
add("adj-06", "fill_blank", "Trotz des ___ Wetters fahren wir los. (schlecht)", "schlechten", t + ["genitive"], "Genitiv → -en.", diag=GEN)
add("adj-07", "fill_blank", "Ich trinke morgens ___ Kaffee. (schwarz)", "schwarzen", t,
    "Ohne Artikel, Akk mask. → -en.", diag=AKK4)
add("adj-08", "fill_blank", "Ich lese die ___ Zeitung. (heutig)", "heutige", t, "der-Wort, Akk fem. → -e.", diag=AKK4)
add("adj-09", "fill_blank", "Wir sprechen mit den ___ Kollegen. (neu)", "neuen", t + ["dative-plural"], "Dativ Plural → -en.", diag=DAT4)
add("adj-10", "fill_blank", "Ich suche eine ___ Wohnung in Zürich. (günstig)", "günstige", t, "ein-Wort, Akk fem. → -e.", diag=AKK4)

# ---- nominalized-participles [8] --------------------------------------------
t = ["nominalized-participles"]
add("np-01", "fill_blank", "Viele ___ stehen jeden Morgen im Stau. (pendeln)", "Pendelnde", t,
    "Wie ein Adjektiv ohne Artikel, Plural Nom → -e.", contrast="pendelnde")
add("np-02", "fill_blank", "Ein ___ wartet am Bahnhof. (pendeln)", "Pendelnder", t,
    "ein + mask. Nom → -er (wie 'ein neuer').", contrast="pendelnde")
add("np-03", "fill_blank", "Die ___ am Gleis 3 müssen umsteigen. (reisen)", "Reisenden", t, "die + Plural → -en.")
add("np-04", "fill_blank", "Für ___ gibt es ein günstiges Abo. (studieren)", "Studierende", t, "Ohne Artikel, Plural Akk → -e.")
add("np-05", "fill_blank", "Der ___ kannte die Strecke gut. (fahren)", "Fahrende", t, "der + Nom Sg. → -e.")
add("np-06", "fill_blank", "Wir haben mit mehreren ___ gesprochen. (reisen)", "Reisenden", t + ["dative-plural"], "Dativ Plural → -en.")

# ---- partizip-ii [8] --------------------------------------------------------
t = ["partizip-ii"]
add("p2-01", "fill_blank", "Ich habe ihn um Hilfe ___. (bitten)", "gebeten", t, "bitten – bat – gebeten (stark).")
add("p2-02", "fill_blank", "Wir haben lange miteinander ___. (sprechen)", "gesprochen", t)
add("p2-03", "fill_blank", "Hast du den Bericht schon ___? (schreiben)", "geschrieben", t)
add("p2-04", "fill_blank", "Ich habe lange eine Wohnung ___. (suchen)", "gesucht", t, "schwach: ge- + -t.")
add("p2-05", "fill_blank", "Die Forscher haben einen Fehler ___. (entdecken)", "entdeckt", t, "Kein ge- nach ent-.")
add("p2-06", "fill_blank", "Der Tunnel wurde 2016 ___. (eröffnen)", "eröffnet", t + ["passive"], "Kein ge- nach er-.")
add("p2-07", "fill_blank", "Das Modell wurde an der ETH ___. (entwickeln)", "entwickelt", t + ["passive", "spelling"], "Kein ge- nach ent-.")
add("p2-08", "fill_blank", "Sie hat das Problem sofort ___. (verstehen)", "verstanden", t, "Kein ge- nach ver-.")
add("p2-09", "error_correction", "Ich habe ihn um Hilfe gebittet.", "Ich habe ihn um Hilfe gebeten.", t, instruction="Finde und korrigiere den Fehler.")
add("p2-10", "error_correction", "Wir haben ein neues Verfahren geentwickelt.", "Wir haben ein neues Verfahren entwickelt.", t,
    instruction="Finde und korrigiere den Fehler.")

# ---- perfekt-aux [8] --------------------------------------------------------
t = ["perfekt-aux"]
add("pa-01", "fill_blank", "Ich ___ heute um sechs aufgestanden. (sein/haben)", "bin", t, "aufstehen → sein (Zustandsänderung).")
add("pa-02", "fill_blank", "Wir ___ mit dem Zug nach Basel gefahren. (sein/haben)", "sind", t, "fahren ohne Objekt → sein.", contrast="fahren-aux")
add("pa-03", "fill_blank", "Ich ___ das Auto in die Garage gefahren. (sein/haben)", "habe", t,
    "fahren mit Akkusativobjekt (das Auto) → haben.", contrast="fahren-aux")
add("pa-04", "fill_blank", "Sie ___ zwei Wochen in Spanien geblieben. (sein/haben)", "ist", t, "bleiben → sein.")
add("pa-05", "error_correction", "Ich habe letztes Jahr nach Rom gereist.", "Ich bin letztes Jahr nach Rom gereist.", t,
    "reisen → sein.", instruction="Finde und korrigiere den Fehler.")
add("pa-06", "transform", "Ich wusste das nicht, deshalb bin ich nicht früher gekommen.",
    ["Wenn ich das gewusst hätte, wäre ich früher gekommen.", "Hätte ich das gewusst, wäre ich früher gekommen."], t,
    "Irreale Vergangenheit: hätte/wäre + Partizip II, nie würde.", instruction="Irreale Vergangenheit (wenn …).")
add("pa-07", "error_correction", "Wenn ich mehr gelernt würde, hätte ich die Prüfung bestanden.",
    "Wenn ich mehr gelernt hätte, hätte ich die Prüfung bestanden.", t + ["wuerde-vs-wurde"],
    "Vergangenheit irreal: hätte + Partizip II.", instruction="Finde und korrigiere den Fehler.")

# ---- passive [8] ------------------------------------------------------------
t = ["passive"]
add("pas-01", "fill_blank", "Die Brücke ___ gerade saniert. (werden, Präsens)", "wird", t, contrast="wird-wurde")
add("pas-02", "fill_blank", "Die Brücke ___ letztes Jahr saniert. (werden, Präteritum)", "wurde", t, contrast="wird-wurde")
add("pas-03", "fill_blank", "Der Plan wurde ___ der Stadt genehmigt.", "von", t, "Agens im Passiv: von + Dativ (nie bei).")
add("pas-04", "error_correction", "Der Fehler wurde bei meinem Kollegen gefunden.", "Der Fehler wurde von meinem Kollegen gefunden.", t,
    "Agens: von + Dativ.", instruction="Finde und korrigiere den Fehler.")
add("pas-05", "transform", "Die Stadt baute den Tunnel 2010.",
    ["Der Tunnel wurde 2010 von der Stadt gebaut.", "Der Tunnel wurde von der Stadt 2010 gebaut."], t,
    instruction="Ins Passiv (Präteritum), mit Agens.")
add("pas-06", "transform", "Man saniert die Straße im Sommer.",
    ["Die Straße wird im Sommer saniert.", "Im Sommer wird die Straße saniert."], t,
    "Im Passiv kein 'man'.", instruction="Ins Passiv (Präsens).")
add("pas-07", "error_correction", "Man wurde die Straße gesperrt.", "Die Straße wurde gesperrt.", t,
    instruction="Finde und korrigiere den Fehler.")
add("pas-08", "fill_blank", "Er ___ 2020 Teil des Teams. (werden, Präteritum)", "wurde", t + ["wuerde-vs-wurde"],
    "werden als Vollverb ('wurde Teil') ist KEIN Passiv.")
add("pas-09", "speaking", "Sag: \"The bridge was built in 1990.\"", "Die Brücke wurde 1990 gebaut.", t + ["wuerde-vs-wurde"],
    "Jahreszahl ohne 'in'.")

# ---- konjunktiv-1 [8] -------------------------------------------------------
t = ["konjunktiv-1"]
add("k1-01", "fill_blank", "Der Professor sagt, er ___ keine Zeit. (haben)", "habe", t)
add("k1-02", "fill_blank", "Sie sagt, sie ___ krank. (sein)", "sei", t)
add("k1-03", "fill_blank", "Er meint, man ___ das Projekt verschieben. (müssen)", "müsse", t)
add("k1-04", "fill_blank", "Sie sagt, sie ___ morgen mitkommen. (wollen)", "wolle", t, "wollen → wolle (ohne Umlaut).")
add("k1-05", "fill_blank", "Er sagt, er ___ das nicht. (können)", "könne", t)
add("k1-06", "fill_blank", "Die Studierenden sagen, sie ___ mehr Zeit. (haben)", "hätten", t,
    "Konj. I 'sie haben' = Indikativ → Konjunktiv II nehmen.")
add("k1-07", "transform", "Der Chef sagt: \"Das Projekt ist machbar.\"",
    ["Der Chef sagt, das Projekt sei machbar.", "Der Chef sagt, dass das Projekt machbar sei."], t,
    instruction="Indirekte Rede (Konjunktiv I).")
add("k1-08", "error_correction", "Er sagt, er wölle mitkommen.", "Er sagt, er wolle mitkommen.", t,
    instruction="Finde und korrigiere den Fehler.")
add("k1-09", "speaking", "Sag in indirekter Rede: Er sagt: \"Ich habe keine Zeit.\"", "Er sagt, er habe keine Zeit.", t)

# ---- dative-plural / genitive / two-way -------------------------------------
t = ["dative-plural"]
add("dp-01", "fill_blank", "Ich spreche mit den ___. (Ingenieur, Pl.)", "Ingenieuren", t, "Dativ Plural: den + -n.")
add("dp-02", "fill_blank", "Sie liest den ___ eine Geschichte vor. (Kind, Pl.)", "Kindern", t)
add("dp-03", "error_correction", "Ich habe mit den Studenten und den Professor gesprochen.",
    "Ich habe mit den Studenten und den Professoren gesprochen.", t, instruction="Finde und korrigiere den Fehler.")
t = ["genitive"]
add("gen-01", "fill_blank", "Wegen ___ Staus kam ich zu spät. (der)", "des", t, "wegen + Genitiv: des Staus.", contrast="wegen-des")
add("gen-02", "error_correction", "Wegen dem Regen bleiben wir zu Hause.", "Wegen des Regens bleiben wir zu Hause.", t,
    "Schriftlich: wegen + Genitiv.", instruction="Finde und korrigiere den Fehler.", contrast="wegen-des")
add("gen-03", "fill_blank", "Trotz ___ Verspätung haben wir den Anschluss erreicht. (die)", "der", t)
add("gen-04", "fill_blank", "Während ___ Semesters arbeite ich Teilzeit. (das)", "des", t)
add("gen-05", "fill_blank", "Innerhalb ___ Stadt gilt Tempo 30. (die)", "der", t)
add("gen-06", "fill_blank", "Aufgrund ___ Bauarbeiten ist die Straße gesperrt. (die, Pl.)", "der", t, "Genitiv Plural immer 'der'.")
add("gen-07", "fill_blank", "Außerhalb ___ Ballungsraums gibt es kaum Busse. (der)", "des", t)
add("gen-08", "fill_blank", "___ ich koche, hört sie Musik. (while)", "Während", t, "während als Konjunktion: Verb am Ende.")
t = ["two-way-prepositions"]
add("tw-01", "fill_blank", "Der Zug fährt in ___ Tunnel. (der)", "den", t, "Richtung (wohin?) → Akkusativ.", diag=WOHIN, contrast="tunnel")
add("tw-02", "fill_blank", "Der Zug hält ___ Tunnel. (in + der)", "im", t, "Ort (wo?) → Dativ: in dem = im.", diag=WO, contrast="tunnel")
add("tw-03", "fill_blank", "Ich hänge das Bild an ___ Wand. (die)", "die", t, diag=WOHIN, contrast="wand")
add("tw-04", "fill_blank", "Das Bild hängt an ___ Wand. (die)", "der", t, diag=WO, contrast="wand")
add("tw-05", "fill_blank", "Das Auto fährt in ___ Kreisverkehr. (der)", "den", t, diag=WOHIN, contrast="kreis")
add("tw-06", "fill_blank", "Das Auto steht ___ Kreisverkehr. (in + der)", "im", t, "Ort (wo?) → Dativ: im.", diag=WO, contrast="kreis")

# ---- relative-clauses / verb-preposition-pairs / zu-infinitive ---------------
t = ["relative-clauses"]
add("rel-01", "fill_blank", "Das ist der Professor, ___ ich gestern geholfen habe.", "dem", t, "helfen + Dativ → dem.")
add("rel-02", "fill_blank", "Die Brücke, ___ wir gebaut haben, ist 200 m lang.", "die", t)
add("rel-03", "fill_blank", "Das ist das Projekt, an ___ ich gerade arbeite.", "dem", t, "arbeiten an + Dativ.")
add("rel-04", "fill_blank", "Der Kollege, mit ___ ich das Gutachten schreibe, ist krank.", "dem", t)
add("rel-05", "fill_blank", "Die Themen, über ___ wir gesprochen haben, sind wichtig.", "die", t, "sprechen über + Akk, Plural.")
add("rel-06", "fill_blank", "Der Mann, ___ Auto im Stau steht, ist mein Nachbar.", "dessen", t)
add("rel-07", "transform", "Ich habe einen Freund. Sein Bruder studiert an der ETH.",
    "Ich habe einen Freund, dessen Bruder an der ETH studiert.", t, instruction="Verbinde mit einem Relativsatz.")
t = ["verb-preposition-pairs"]
add("vp-01", "fill_blank", "Ich warte ___ den Bus.", "auf", t)
add("vp-02", "fill_blank", "Ich freue mich ___ das Wochenende.", "auf", t, "sich freuen auf (Zukunft) + Akk.")
add("vp-03", "fill_blank", "Ich bereite mich ___ die Prüfung vor.", "auf", t + ["reflexive"])
add("vp-04", "fill_blank", "Das hängt ___ Wetter ab. (von + dem)", "vom", t)
add("vp-05", "fill_blank", "Die Brücke besteht ___ Stahl und Beton.", "aus", t)
add("vp-06", "fill_blank", "Ich nehme ___ Kurs teil. (an + dem)", "am", t, "teilnehmen an + Dativ.")
add("vp-07", "fill_blank", "Ich schicke die E-Mail ___ den Professor.", "an", t)
add("vp-08", "fill_blank", "Ich bin gespannt ___ das Ergebnis.", "auf", t, "gespannt auf + Nomen.", contrast="gespannt")
add("vp-09", "fill_blank", "Ich bin gespannt, ___ das Projekt ausgeht. (auf + wie)", "darauf, wie", t,
    "Vor einem Nebensatz: darauf, wie/ob/dass …", contrast="gespannt")
add("vp-10", "fill_blank", "Er ist Professor ___ Verkehrsplanung.", "für", t)
add("vp-11", "speaking", "Sag: \"I'm looking forward to the weekend.\"",
    ["Ich freue mich auf das Wochenende.", "Ich freue mich aufs Wochenende."], t + ["reflexive"])
t = ["zu-infinitive"]
add("zu-01", "fill_blank", "Ich versuche, jeden Tag Deutsch ___. (sprechen)", "zu sprechen", t)
add("zu-02", "fill_blank", "Ich muss morgen früh ___. (aufstehen)", "aufstehen", t, "Nach Modalverben kein zu.", contrast="zu-modal")
add("zu-03", "fill_blank", "Ich habe vor, morgen früh ___. (aufstehen)", "aufzustehen", t, "Trennbare Verben: auf-zu-stehen.", contrast="zu-modal")
add("zu-04", "fill_blank", "Ich fahre nach Zürich, ___ dort zu arbeiten.", "um", t, "Zweck: um … zu.")
add("zu-05", "fill_blank", "Laufen hilft mir, den Kopf ___. (freibekommen)", "freizubekommen", t)
add("zu-06", "error_correction", "Ich hoffe, dich bald sehen.", "Ich hoffe, dich bald zu sehen.", t, instruction="Finde und korrigiere den Fehler.")
add("zu-07", "error_correction", "Ich kann morgen nicht zu kommen.", "Ich kann morgen nicht kommen.", t, instruction="Finde und korrigiere den Fehler.")

# ---- [6] and [5] patterns ---------------------------------------------------
t = ["als-wenn-wie"]
add("aw-01", "fill_blank", "___ ich 2019 nach Zürich kam, sprach ich kaum Deutsch.", "Als", t, "Einmaliges Ereignis in der Vergangenheit → als.")
add("aw-02", "fill_blank", "Immer ___ es regnet, nehme ich den Bus.", "wenn", t, "Wiederholung → wenn.")
add("aw-03", "fill_blank", "Zürich ist teurer ___ Madrid.", "als", t, "Komparativ + als.", contrast="als-wie")
add("aw-04", "fill_blank", "Der Tunnel ist so lang ___ die Brücke.", "wie", t, "so … wie.", contrast="als-wie")
add("aw-05", "error_correction", "Wenn ich gestern nach Hause kam, war niemand da.", "Als ich gestern nach Hause kam, war niemand da.", t,
    instruction="Finde und korrigiere den Fehler.")
t = ["superfluous-es"]
add("es-01", "error_correction", "Ich habe es versucht, früher zu kommen.", "Ich habe versucht, früher zu kommen.", t, instruction="Finde und korrigiere den Fehler.")
add("es-02", "error_correction", "Ich habe es gesehen, dass du angerufen hast.", "Ich habe gesehen, dass du angerufen hast.", t, instruction="Finde und korrigiere den Fehler.")
t = ["je-desto"]
add("jd-01", "transform", "Je mehr ich übe, … (ich spreche flüssiger)", "Je mehr ich übe, desto flüssiger spreche ich.", t,
    "Nach desto + Komparativ kommt direkt das Verb.", instruction="Ergänze mit desto.")
add("jd-02", "error_correction", "Je früher wir anfangen, desto wir sind schneller fertig.", "Je früher wir anfangen, desto schneller sind wir fertig.", t,
    instruction="Finde und korrigiere den Fehler.")
t = ["modal-praeteritum"]
add("mp-01", "fill_blank", "Als Kind ___ ich keinen Spinat. (mögen, Präteritum)", "mochte", t, "mochte = Präteritum von mögen.", contrast="mochte")
add("mp-02", "fill_blank", "Ich ___ bitte einen Kaffee. (höflicher Wunsch)", "möchte", t, "möchte = Konjunktiv II (Wunsch).", contrast="mochte")
add("mp-03", "fill_blank", "Gestern ___ ich nicht kommen. (können)", "konnte", t, "Präteritum der Modalverben ohne Umlaut.")
add("mp-04", "fill_blank", "Wir ___ den Bericht bis Freitag abgeben. (müssen, Präteritum)", "mussten", t)
add("mp-05", "fill_blank", "Er ___ nicht mitkommen. (dürfen, Präteritum)", "durfte", t)
add("mp-06", "error_correction", "Ich wollte habe mehr Zeit.", "Ich wollte mehr Zeit haben.", t, "Modalverb + Infinitiv am Ende.",
    instruction="Finde und korrigiere den Fehler.")
t = ["reflexive"]
add("rf-01", "fill_blank", "Der Zug hat ___ um zehn Minuten verspätet.", "sich", t)
add("rf-02", "fill_blank", "Ich muss ___ das noch überlegen.", "mir", t, "sich (Dativ) etwas überlegen.")
add("rf-03", "fill_blank", "Das Büro befindet ___ im dritten Stock.", "sich", t)
add("rf-04", "fill_blank", "Ich fühle ___ hier sehr wohl.", "mich", t)
add("rf-05", "error_correction", "Der Zug hat sich um zehn Minuten verschoben.", "Der Zug hat sich um zehn Minuten verspätet.", t + ["vocab"],
    "sich verspäten = to be late. sich verschieben = to be postponed/shift.", instruction="Finde und korrigiere den Fehler.")
t = ["nominalization-from-adjective"]
for i, (adj, noun, en) in enumerate([("breit", "die Breite", "width"), ("lang", "die Länge", "length"), ("hoch", "die Höhe", "height"),
                                       ("tief", "die Tiefe", "depth"), ("stark", "die Stärke", "strength"), ("schwer", "das Gewicht", "weight")], 1):
    add(f"na-{i:02d}", "vocab", f"{adj} → Nomen (mit Artikel)", noun, t + ["noun-gender"], f"{noun} = {en}")

# ---- 2-sentence mini-texts (progressively longer) ---------------------------
add("mt-01", "error_correction",
    "Letzte Woche ich habe meinen Onkel besucht. Weil er hatte keine Zeit, wir sind nur kurz geblieben.",
    "Letzte Woche habe ich meinen Onkel besucht. Weil er keine Zeit hatte, sind wir nur kurz geblieben.",
    ["v2-main-clause", "weil-subject-position", "verb-final"],
    "V2 im Hauptsatz; Verb am Ende im weil-Satz; nach dem Nebensatz kommt sofort das Verb.", instruction="Korrigiere den Mini-Text (3 Fehler).")
add("mt-02", "error_correction",
    "Die Brücke würde 1990 gebaut. Heute wird sie von den Ingenieuren saniert, weil sie ist zu alt.",
    "Die Brücke wurde 1990 gebaut. Heute wird sie von den Ingenieuren saniert, weil sie zu alt ist.",
    ["wuerde-vs-wurde", "passive", "verb-final"], instruction="Korrigiere den Mini-Text (2 Fehler).")
add("mt-03", "error_correction",
    "Wegen dem Stau ich bin zu spät gekommen. Der Professor hat gesagt, dass ich soll die E-Mail an ihm schicken.",
    "Wegen des Staus bin ich zu spät gekommen. Der Professor hat gesagt, dass ich die E-Mail an ihn schicken soll.",
    ["genitive", "v2-main-clause", "verb-final", "verb-preposition-pairs"], instruction="Korrigiere den Mini-Text (4 Fehler).")

with open(os.path.join(OUT, "01-grammar.json"), "w", encoding="utf-8") as f:
    json.dump({"source": SRC, "cards": cards}, f, ensure_ascii=False, indent=1)
grammar_count = len(cards)
cards = []

# ---- noun-gender: show article only -----------------------------------------
genders = {"die": "E-Mail Zukunft Welt Karriere Option Arbeit Art Woche Brücke Zeit Gelegenheit Arbeitsstelle".split(),
           "der": "Beruf Marathon Plan Vortritt Kreisverkehr Alltag Verkehr Stau Engpass Ausbau Nutzen".split(),
           "das": "Projekt Detail Semester Studium Fahrrad Land Team Gutachten Vorhaben Pendleraufkommen".split()}
for art, nouns in genders.items():
    for n in nouns:
        add(f"gd-{n.lower()}", "fill_blank", f"___ {n}", art, ["noun-gender"])
add("gd-rule-fem", "fill_blank", "Endung -ung / -ion / -tät / -heit / -keit → ___ (Artikel)", "die", ["noun-gender"],
    "Diese Endungen sind immer feminin: die Zeitung, die Option, die Kapazität, die Sicherheit, die Machbarkeit.")

# ---- spelling ---------------------------------------------------------------
t = ["spelling"]
for i, (en, de, note) in enumerate([
        ("perhaps", "vielleicht", None), ("on the other hand", "andererseits", None),
        ("to be interested (in)", "sich interessieren", "interessieren mit Doppel-s und ie."),
        ("to train", "trainieren", "-ieren"), ("feasibility", "die Machbarkeit", None),
        ("results", "die Ergebnisse", None), ("yesterday", "gestern", None), ("train station", "der Bahnhof", None),
        ("viewpoints (lookouts)", "die Aussichtspunkte", None), ("street", "die Straße", "ß nach langem Vokal."),
        ("to enjoy", "genießen", None), ("outside of", "außerhalb", None), ("ability", "die Fähigkeit", None),
        ("master's thesis topic (one word)", "das Masterarbeitsthema", "Komposita zusammenschreiben."),
        ("road intersection (one word)", "die Straßenkreuzung", None), ("job / workplace", "die Arbeitsstelle", None),
        ("alcohol", "der Alkohol", "Spanisch 'alcohol' → Deutsch mit k und h."), ("project", "das Projekt", "Mit k und t, nicht 'proyecto'."),
        ("dimension", "die Dimension", None), ("to have to (wir)", "wir müssen", None), ("height", "die Höhe", None)], 1):
    add(f"sp-{i:02d}", "vocab", en, de, t, note)
add("sp-schoen", "fill_blank", "Das Wetter ist heute sehr ___. (beautiful)", "schön", t, "schön = beautiful; schon = already.", contrast="schon")
add("sp-schon", "fill_blank", "Ich bin ___ fertig. (already)", "schon", t, "schon = already; schön = beautiful.", contrast="schon")
add("sp-1990", "error_correction", "Ich bin in 1990 geboren.", "Ich bin 1990 geboren.", t,
    "Jahreszahlen ohne 'in' (oder: im Jahr 1990).", instruction="Finde und korrigiere den Fehler.")
add("sp-rom", "error_correction", "Wir planen ein Rom trip.",
    ["Wir planen eine Reise nach Rom.", "Wir planen einen Trip nach Rom.", "Wir planen eine Romreise."], t,
    "Kein englisches Kompositum mit Leerzeichen.", instruction="Sag es auf Deutsch richtig.")
add("sp-zukunft", "vocab", "in the future", ["in Zukunft", "künftig", "zukünftig"], t + ["vocab"])

with open(os.path.join(OUT, "02-gender-spelling.json"), "w", encoding="utf-8") as f:
    json.dump({"source": SRC, "cards": cards}, f, ensure_ascii=False, indent=1)
gs_count = len(cards)
cards = []

# ---- vocabulary and collocations: EN → DE and DE → EN -----------------------
vocab = [
    ("sich verspäten", ["to be late", "to be delayed"], "Der Zug hat sich um zehn Minuten verspätet."),
    ("verlaufen", ["to go", "to proceed", "to run"], "Die Sitzung ist gut verlaufen."),
    ("ermöglichen", ["to enable", "to make possible"], "Der neue Tunnel ermöglicht kürzere Fahrzeiten."),
    ("bestehen (eine Prüfung)", ["to pass"], "Ich habe die Prüfung bestanden."),
    ("die Abgabe", ["submission", "hand-in"], "Die Abgabe der Masterarbeit ist im Mai."),
    ("gestalten", ["to design", "to shape"], "Wir gestalten den Platz verkehrsberuhigt."),
    ("die Einschätzung", ["assessment", "estimate"], "Nach meiner Einschätzung ist das machbar."),
    ("die Auswirkung", ["impact", "effect"], "Die Auswirkungen auf den Verkehr sind groß."),
    ("der Handlungsbedarf", ["need for action"], "Beim Lärmschutz besteht Handlungsbedarf."),
    ("die Machbarkeitsstudie", ["feasibility study"], "Die Machbarkeitsstudie ist abgeschlossen."),
    ("der Zielkonflikt", ["trade-off", "conflict of objectives"], "Es gibt einen Zielkonflikt zwischen Kosten und Sicherheit."),
    ("die Voraussetzung", ["prerequisite", "requirement"], "Deutsch C1 ist eine Voraussetzung."),
    ("die Auflage", ["condition", "requirement"], "Die Baubewilligung kam mit Auflagen."),
    ("die Zuständigkeit", ["responsibility", "jurisdiction"], "Das liegt in der Zuständigkeit des Kantons."),
    ("die Rahmenbedingungen", ["framework conditions", "general conditions"], "Die Rahmenbedingungen haben sich geändert."),
    ("die Gegebenheiten", ["circumstances", "local conditions"], "Wir passen das Projekt den örtlichen Gegebenheiten an."),
    ("die Vorgabe", ["specification", "guideline", "target"], "Die Vorgaben des Bundes sind streng."),
    ("die Bestandsaufnahme", ["inventory", "stocktaking"], "Zuerst machen wir eine Bestandsaufnahme."),
    ("der Ballungsraum", ["metropolitan area", "conurbation"], "Im Ballungsraum Zürich wächst der Verkehr."),
    ("die Anbindung", ["connection", "link"], "Die Anbindung an die S-Bahn ist gut."),
    ("die Kapazität", ["capacity"], "Die Kapazität der Strecke ist erreicht."),
    ("der Engpass", ["bottleneck"], "Der Engpass liegt vor dem Tunnel."),
    ("die Umleitung", ["detour", "diversion"], "Wegen der Bauarbeiten gibt es eine Umleitung."),
    ("der Verkehrsknoten", ["traffic hub", "junction"], "Der Bahnhof ist ein wichtiger Verkehrsknoten."),
    ("das Verkehrsaufkommen", ["traffic volume"], "Das Verkehrsaufkommen steigt jedes Jahr."),
    ("die Verkehrsberuhigung", ["traffic calming"], "Die Verkehrsberuhigung reduziert den Lärm."),
]
for i, (de, en, ex) in enumerate(vocab, 1):
    answer = de.replace(" (eine Prüfung)", "")
    add(f"vo-{i:02d}-en", "vocab", en[0] + " (DE?)", answer, ["vocab"], example=ex,
        hint="Mit Artikel bei Nomen." if de.startswith(("der ", "die ", "das ")) else None)
    add(f"vo-{i:02d}-de", "vocab", de + " (EN?)", en, ["vocab"], example=ex)

with open(os.path.join(OUT, "03-vocab.json"), "w", encoding="utf-8") as f:
    json.dump({"source": SRC, "cards": cards}, f, ensure_ascii=False, indent=1)
print(f"grammar={grammar_count} gender+spelling={gs_count} vocab={len(cards)}")
