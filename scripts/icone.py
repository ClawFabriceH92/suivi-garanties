#!/usr/bin/env python3
"""Génère l'icône de Mes Garanties : petit personnage avec une clé à molette.

Dessin vectoriel via pycairo dans un repère 108x108 (le repère des icônes
adaptatives Android), rendu à 10x puis réduit aux densités voulues.
La zone sûre d'une icône adaptative est un carré de 66x66 centré : le
personnage reste dedans pour ne pas être rogné par les masques du launcher.
"""
import math
import os
import cairo
from PIL import Image

ECHELLE = 10
COTE = 108
PIXELS = COTE * ECHELLE

BLEU = (0x1B / 255, 0x5E / 255, 0x9B / 255)
BLEU_CASQUETTE = (0x2F / 255, 0x6F / 255, 0xB2 / 255)
PEAU = (0xF6 / 255, 0xC8 / 255, 0x9F / 255)
SALOPETTE = (0xF2 / 255, 0x99 / 255, 0x4A / 255)
SALOPETTE_OMBRE = (0xD9 / 255, 0x80 / 255, 0x35 / 255)
ACIER = (0xBC / 255, 0xC7 / 255, 0xD2 / 255)
ACIER_OMBRE = (0x7C / 255, 0x8B / 255, 0x99 / 255)
TRAIT = (0x2B / 255, 0x35 / 255, 0x40 / 255)
BLANC = (1, 1, 1)


def disque(ctx, x, y, r, couleur):
    ctx.set_source_rgb(*couleur)
    ctx.arc(x, y, r, 0, 2 * math.pi)
    ctx.fill()


def disque_transparent(ctx, x, y, r):
    """Perce un trou : le fond de l'icône (ou le calque inférieur) réapparaît."""
    ctx.save()
    ctx.set_operator(cairo.OPERATOR_CLEAR)
    ctx.arc(x, y, r, 0, 2 * math.pi)
    ctx.fill()
    ctx.restore()


def rectangle_arrondi(ctx, x, y, largeur, hauteur, rayon, couleur):
    ctx.set_source_rgb(*couleur)
    ctx.new_sub_path()
    rayon = min(rayon, largeur / 2, hauteur / 2)
    ctx.arc(x + largeur - rayon, y + rayon, rayon, -math.pi / 2, 0)
    ctx.arc(x + largeur - rayon, y + hauteur - rayon, rayon, 0, math.pi / 2)
    ctx.arc(x + rayon, y + hauteur - rayon, rayon, math.pi / 2, math.pi)
    ctx.arc(x + rayon, y + rayon, rayon, math.pi, 3 * math.pi / 2)
    ctx.close_path()
    ctx.fill()


def cle_a_molette(ctx, x, y, angle, hauteur_manche=26.0, largeur_manche=6.4):
    """Clé à molette : anneau en bas, manche, tête à mâchoire ouverte en haut."""
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(angle)

    # manche
    rectangle_arrondi(
        ctx, -largeur_manche / 2, 0, largeur_manche, hauteur_manche, largeur_manche / 2, ACIER
    )
    # reflet du manche
    rectangle_arrondi(
        ctx, -largeur_manche / 2 + 1.1, 1.5, 1.6, hauteur_manche - 3, 0.8, BLANC
    )

    # anneau du bas
    disque(ctx, 0, hauteur_manche + 5.4, 7.2, ACIER)
    disque_transparent(ctx, 0, hauteur_manche + 5.4, 3.1)

    # tête : disque plein puis mâchoire creusée
    disque(ctx, 0, -4.2, 9.2, ACIER)
    ctx.save()
    ctx.set_operator(cairo.OPERATOR_CLEAR)
    ctx.move_to(-9.5, -12.5)
    ctx.line_to(-1.2, -6.0)
    ctx.line_to(-1.2, 1.0)
    ctx.line_to(-10.5, -3.0)
    ctx.close_path()
    ctx.fill()
    ctx.restore()

    # molette crantée (le petit détail qui fait reconnaître l'outil)
    ctx.set_source_rgb(*ACIER_OMBRE)
    ctx.set_line_width(1.5)
    ctx.arc(3.4, -1.0, 3.0, -0.6, 2.4)
    ctx.stroke()

    ctx.restore()


def dessiner():
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, PIXELS, PIXELS)
    ctx = cairo.Context(surface)
    ctx.scale(ECHELLE, ECHELLE)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)

    # ---- corps / salopette
    rectangle_arrondi(ctx, 39.5, 62, 29, 24, 9, SALOPETTE)
    # bretelles
    rectangle_arrondi(ctx, 44.0, 57.5, 4.2, 10, 2.0, SALOPETTE_OMBRE)
    rectangle_arrondi(ctx, 59.8, 57.5, 4.2, 10, 2.0, SALOPETTE_OMBRE)
    # poche
    rectangle_arrondi(ctx, 49.5, 70.5, 9, 7, 2.0, SALOPETTE_OMBRE)

    # ---- bras droit levé (tient la clé)
    ctx.set_source_rgb(*SALOPETTE)
    ctx.set_line_width(7.5)
    ctx.move_to(66.0, 66.0)
    ctx.curve_to(71.5, 63.0, 74.0, 58.5, 74.5, 53.0)
    ctx.stroke()

    # ---- tête
    disque(ctx, 54, 43.5, 14.2, PEAU)
    # oreilles
    disque(ctx, 39.4, 44.5, 2.9, PEAU)
    disque(ctx, 68.6, 44.5, 2.9, PEAU)

    # ---- casquette
    ctx.set_source_rgb(*BLEU_CASQUETTE)
    ctx.new_path()
    ctx.arc(54, 43.5, 14.6, math.pi * 1.04, math.pi * 1.96)
    ctx.close_path()
    ctx.fill()
    # visière
    rectangle_arrondi(ctx, 53.0, 27.6, 17.5, 4.6, 2.3, BLEU_CASQUETTE)
    # bouton du casque
    disque(ctx, 54, 29.2, 2.0, BLEU)

    # ---- visage
    disque(ctx, 48.6, 45.0, 1.9, TRAIT)
    disque(ctx, 59.4, 45.0, 1.9, TRAIT)
    # reflet des yeux
    disque(ctx, 49.2, 44.3, 0.7, BLANC)
    disque(ctx, 60.0, 44.3, 0.7, BLANC)
    # sourire
    ctx.set_source_rgb(*TRAIT)
    ctx.set_line_width(1.9)
    ctx.arc(54, 47.4, 4.6, 0.35 * math.pi, 0.65 * math.pi)
    ctx.stroke()
    # joues
    ctx.set_source_rgba(0.93, 0.45, 0.40, 0.35)
    ctx.arc(44.6, 49.6, 2.4, 0, 2 * math.pi)
    ctx.fill()
    ctx.arc(63.4, 49.6, 2.4, 0, 2 * math.pi)
    ctx.fill()

    # ---- main qui tient l'outil
    cle_a_molette(ctx, 74.8, 52.0, math.radians(-22))
    disque(ctx, 74.4, 51.6, 4.4, PEAU)

    return surface


def main():
    surface = dessiner()
    sortie = "/tmp/icone/rendu.png"
    os.makedirs(os.path.dirname(sortie), exist_ok=True)
    surface.write_to_png(sortie)

    image = Image.open(sortie).convert("RGBA")
    # Densités Android du premier plan adaptatif (108 dp par densité).
    for densite, cote in {
        "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
    }.items():
        redimensionnee = image.resize((cote, cote), Image.LANCZOS)
        dossier = f"/tmp/icone/mipmap-{densite}"
        os.makedirs(dossier, exist_ok=True)
        redimensionnee.save(f"{dossier}/ic_launcher_foreground.png")
    # Planches de contrôle (fond bleu de l'app, comme le launcher).
    for cote, nom in ((432, "apercu-xxxhdpi.png"), (96, "apercu-petit.png")):
        fond = Image.new("RGBA", (cote, cote), (0x1B, 0x5E, 0x9B, 255))
        premier_plan = image.resize((cote, cote), Image.LANCZOS)
        fond.alpha_composite(premier_plan)
        fond.save(f"/tmp/icone/{nom}")
    print("rendu ok", surface.get_width())


if __name__ == "__main__":
    main()
