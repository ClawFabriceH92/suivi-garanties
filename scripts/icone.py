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
ACIER = (0xAE / 255, 0xBB / 255, 0xC8 / 255)
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


def cle_a_molette(ctx, x, y, angle, longueur=46.0, largeur_manche=6.6):
    """Clé à molette debout : mâchoire ouverte en haut, manche, anneau en bas.

    Dessinée dans son propre repère (origine = centre de l'outil), puis posée
    à côté du personnage : elle doit se lire comme un objet distinct, pas comme
    un accessoire tenu à la main.
    """
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(angle)
    demi = longueur / 2

    # manche
    rectangle_arrondi(
        ctx, -largeur_manche / 2, -demi + 14, largeur_manche, longueur - 24,
        largeur_manche / 2, ACIER,
    )
    # reflet du manche
    rectangle_arrondi(
        ctx, -largeur_manche / 2 + 1.2, -demi + 16, 1.7, longueur - 28, 0.85, BLANC,
    )

    # anneau du bas
    disque(ctx, 0, demi - 5.6, 7.0, ACIER)
    disque_transparent(ctx, 0, demi - 5.6, 3.0)

    # tête : disque plein puis mâchoire ouverte (fente + gorge)
    disque(ctx, 0, -demi + 8.4, 9.0, ACIER)
    ctx.save()
    ctx.set_operator(cairo.OPERATOR_CLEAR)
    # fente de la mâchoire, orientée à 45° vers le haut-droite
    ctx.save()
    ctx.translate(0, -demi + 8.4)
    ctx.rotate(math.radians(38))
    ctx.rectangle(-1.9, -12.5, 3.8, 11.0)
    ctx.restore()
    ctx.fill()
    ctx.restore()

    # molette de réglage
    ctx.set_source_rgb(*ACIER_OMBRE)
    ctx.set_line_width(1.6)
    ctx.arc(-3.6, -demi + 13.6, 2.6, -0.4, 2.6)
    ctx.stroke()

    ctx.restore()


def bonhomme(ctx):
    """Bonhomme de face : casquette, salopette, bras et jambes visibles."""
    # ---- jambes
    rectangle_arrondi(ctx, 33.6, 70.0, 5.0, 14.0, 2.4, SALOPETTE_OMBRE)
    rectangle_arrondi(ctx, 43.0, 70.0, 5.0, 14.0, 2.4, SALOPETTE_OMBRE)
    # chaussures
    rectangle_arrondi(ctx, 32.4, 82.0, 7.2, 5.0, 2.4, TRAIT)
    rectangle_arrondi(ctx, 41.8, 82.0, 7.2, 5.0, 2.4, TRAIT)

    # ---- bras
    ctx.set_source_rgb(*PEAU)
    ctx.set_line_width(4.6)
    for depart_x, arrivee_x in ((33.4, 30.2), (48.2, 51.4)):
        ctx.move_to(depart_x, 55.5)
        ctx.line_to(arrivee_x, 65.5)
        ctx.stroke()

    # ---- corps / salopette
    rectangle_arrondi(ctx, 30.6, 50.5, 20.4, 21.5, 5.5, SALOPETTE)
    # bretelles
    rectangle_arrondi(ctx, 33.8, 46.0, 3.8, 8.0, 1.8, SALOPETTE_OMBRE)
    rectangle_arrondi(ctx, 44.0, 46.0, 3.8, 8.0, 1.8, SALOPETTE_OMBRE)
    # poche
    rectangle_arrondi(ctx, 36.4, 59.5, 8.8, 6.4, 1.8, SALOPETTE_OMBRE)

    # ---- tête
    disque(ctx, 40.8, 36.0, 11.4, PEAU)
    disque(ctx, 29.8, 37.2, 2.5, PEAU)
    disque(ctx, 51.8, 37.2, 2.5, PEAU)

    # ---- casquette
    ctx.set_source_rgb(*BLEU_CASQUETTE)
    ctx.new_path()
    ctx.arc(40.8, 36.0, 11.8, math.pi * 1.02, math.pi * 1.98)
    ctx.close_path()
    ctx.fill()
    rectangle_arrondi(ctx, 40.6, 22.4, 14.0, 3.9, 1.95, BLEU_CASQUETTE)
    disque(ctx, 40.8, 23.6, 1.7, BLEU)

    # ---- visage
    disque(ctx, 36.6, 37.4, 1.65, TRAIT)
    disque(ctx, 45.0, 37.4, 1.65, TRAIT)
    disque(ctx, 37.1, 36.8, 0.6, BLANC)
    disque(ctx, 45.5, 36.8, 0.6, BLANC)
    ctx.set_source_rgb(*TRAIT)
    ctx.set_line_width(1.7)
    ctx.arc(40.8, 39.6, 3.8, 0.3 * math.pi, 0.7 * math.pi)
    ctx.stroke()
    ctx.set_source_rgba(0.93, 0.45, 0.40, 0.32)
    ctx.arc(32.8, 41.4, 2.1, 0, 2 * math.pi)
    ctx.fill()
    ctx.arc(48.8, 41.4, 2.1, 0, 2 * math.pi)
    ctx.fill()


def dessiner():
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, PIXELS, PIXELS)
    ctx = cairo.Context(surface)
    ctx.scale(ECHELLE, ECHELLE)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)

    bonhomme(ctx)
    # La clé est nettement à droite, sans contact avec le personnage.
    cle_a_molette(ctx, 70.5, 52.0, math.radians(-7))

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
