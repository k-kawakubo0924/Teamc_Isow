package com.teamc.isow.backend.image;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;

/**
 * 展開した画像を、長辺が上限に収まるように縮める（docs/post.md「写真の大きさ」）。
 *
 * <p>縮めた先の1画素に重なる元の画素を、重なっている面積の割合で平均する（面積平均）。
 * 画素を間引くだけの縮め方と違い、服の細かい柄が縞模様（モアレ）になりにくい。
 *
 * <p>向きを直す処理（ImageMetadataStripper）と同じく、色の変換をしないよう、画素の値をそのまま計算する
 * （色空間は元の画像のものを使う。ICC プロファイルは、この後で元の画像から付け直す）。
 * 透明な部分がある画像は、透明度で重みを付けて平均する（透明な画素の色が、縁に黒くにじまないように）。
 */
final class ImageResizer {

    private ImageResizer() {
    }

    /** 長辺が maxLongSide を超える場合だけ、縦横の比を保って長辺を maxLongSide に縮める。超えなければそのまま返す */
    static BufferedImage fitWithin(BufferedImage image, int maxLongSide) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (Math.max(width, height) <= maxLongSide) {
            return image;
        }
        double scale = (double) maxLongSide / Math.max(width, height);
        int targetWidth = width >= height ? maxLongSide : Math.max(1, (int) Math.round(width * scale));
        int targetHeight = width >= height ? Math.max(1, (int) Math.round(height * scale)) : maxLongSide;

        // パレットの画像（色の番号で持つ PNG）は、番号を平均しても意味がないため、色の値に直してから縮める
        BufferedImage source = image.getColorModel() instanceof IndexColorModel palette
                ? palette.convertToIntDiscrete(image.getRaster(), palette.hasAlpha())
                : image;
        return resize(source, targetWidth, targetHeight);
    }

    private static BufferedImage resize(BufferedImage source, int targetWidth, int targetHeight) {
        ColorModel colorModel = source.getColorModel();
        Raster raster = source.getRaster();
        int width = source.getWidth();
        int bands = raster.getNumBands();
        // 透明度は最後の値（ColorModel の決まり）。あらかじめ掛け合わせてある画像は、そのまま平均すればよい
        boolean weightByAlpha = colorModel.hasAlpha() && !colorModel.isAlphaPremultiplied();
        int alpha = bands - 1;

        Span[] columns = spans(width, targetWidth);
        Span[] rows = spans(source.getHeight(), targetHeight);
        WritableRaster target = colorModel.createCompatibleWritableRaster(targetWidth, targetHeight);
        int[] sourceRow = new int[width * bands];
        double[] sums = new double[targetWidth * bands];
        int[] targetRow = new int[targetWidth * bands];

        for (int ty = 0; ty < targetHeight; ty++) {
            Arrays.fill(sums, 0);
            Span row = rows[ty];
            for (int i = 0; i < row.weights().length; i++) {
                raster.getPixels(0, row.start() + i, width, 1, sourceRow);
                double rowWeight = row.weights()[i];
                for (int tx = 0; tx < targetWidth; tx++) {
                    Span column = columns[tx];
                    int out = tx * bands;
                    for (int j = 0; j < column.weights().length; j++) {
                        double weight = rowWeight * column.weights()[j];
                        int in = (column.start() + j) * bands;
                        double colorWeight = weightByAlpha ? weight * sourceRow[in + alpha] : weight;
                        for (int b = 0; b < bands; b++) {
                            sums[out + b] += sourceRow[in + b] * (weightByAlpha && b != alpha ? colorWeight : weight);
                        }
                    }
                }
            }
            for (int tx = 0; tx < targetWidth; tx++) {
                int out = tx * bands;
                for (int b = 0; b < bands; b++) {
                    double value = sums[out + b];
                    if (weightByAlpha && b != alpha) {
                        // 色は透明度で重みを付けた合計なので、透明度の合計で割り戻す（すべて透明なら色は 0）
                        value = sums[out + alpha] == 0 ? 0 : value / sums[out + alpha];
                    }
                    targetRow[out + b] = (int) Math.round(value);
                }
            }
            target.setPixels(0, ty, targetWidth, 1, targetRow);
        }
        return new BufferedImage(colorModel, target, source.isAlphaPremultiplied(), null);
    }

    /**
     * 縮めた先の画素ごとに、重なる元の画素の範囲（start から weights の数だけ）と、重なっている割合（合計 1）を求める
     */
    private static Span[] spans(int sourceLength, int targetLength) {
        double ratio = (double) sourceLength / targetLength;
        Span[] spans = new Span[targetLength];
        for (int t = 0; t < targetLength; t++) {
            double from = t * ratio;
            double to = (t + 1) * ratio;
            int start = (int) Math.floor(from);
            int end = Math.min(sourceLength, (int) Math.ceil(to));
            double[] weights = new double[end - start];
            for (int s = start; s < end; s++) {
                weights[s - start] = (Math.min(to, s + 1) - Math.max(from, s)) / ratio;
            }
            spans[t] = new Span(start, weights);
        }
        return spans;
    }

    private record Span(int start, double[] weights) {
    }
}
