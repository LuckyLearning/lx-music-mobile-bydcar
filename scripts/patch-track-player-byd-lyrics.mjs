/**
 * 给钉死的 react-native-track-player 补上迪链要的歌词字段。
 *
 * 依赖是 git commit，不改 package-lock。Gradle 配置阶段跑这个脚本，
 * 在编译播放器模块之前改 node_modules 里的两份 Java。重复执行是空操作。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = process.env.BYD_LYRIC_PATCH_ROOT
  ?? join(dirname(fileURLToPath(import.meta.url)), '..')

const metadataPath = join(
  root,
  'node_modules/react-native-track-player/android/src/main/java/com/guichaguri/trackplayer/service/metadata/MetadataManager.java',
)
const trackMetadataPath = join(
  root,
  'node_modules/react-native-track-player/android/src/main/java/com/guichaguri/trackplayer/service/models/TrackMetadata.java',
)

const MARK = 'absorbBydLyric'
const BYD_KEY = 'com.byd.mediacenter.metadata.LYRICS'

const read = (path) => {
  try {
    return readFileSync(path, 'utf8')
  } catch (error) {
    if (error && error.code === 'ENOENT') return null
    throw error
  }
}

const fail = (message) => {
  console.error(message)
  process.exit(1)
}

const replaceOnce = (source, from, to, label) => {
  if (!source.includes(from)) fail(`BYD lyric patch: anchor not found (${label})`)
  const next = source.replace(from, to)
  if (next === source) fail(`BYD lyric patch: anchor did not change (${label})`)
  return next
}

const metadata = read(metadataPath)
const trackMetadata = read(trackMetadataPath)
if (metadata == null || trackMetadata == null) {
  console.log('BYD lyric session patch skipped (react-native-track-player is not installed)')
  process.exit(0)
}

if (metadata.includes(MARK) && trackMetadata.includes(BYD_KEY)) {
  console.log('BYD lyric session patch already applied')
  process.exit(0)
}
if (metadata.includes(MARK) || trackMetadata.includes(BYD_KEY)) {
  fail('BYD lyric patch is partial. Reinstall react-native-track-player at the pinned commit and rebuild.')
}

let nextMetadata = metadata
nextMetadata = replaceOnce(
  nextMetadata,
  `    private Action previousAction, rewindAction, playAction, pauseAction, stopAction, forwardAction, nextAction;\n`,
  `    private Action previousAction, rewindAction, playAction, pauseAction, stopAction, forwardAction, nextAction;\n\n    // [BYD] 迪链读的歌词。播放状态每次重建都要带上，否则下一拍就被冲掉。\n    private final Bundle bydLyricExtras = new Bundle();\n`,
  'field',
)

nextMetadata = replaceOnce(
  nextMetadata,
  `        MediaMetadataCompat.Builder metadata = track.toMediaMetadata();\n\n        metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);`,
  `        MediaMetadataCompat.Builder metadata = track.toMediaMetadata();\n        applyBydLyricMetadata(metadata);\n\n        metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);`,
  'artwork',
)

nextMetadata = replaceOnce(
  nextMetadata,
  `    public void updateTitles(Bundle data) {
      if (prevMetadata == null) return;
      if (data.containsKey("title")) {
        prevMetadata.putString(METADATA_KEY_TITLE, data.getString("title"));
      }
      if (data.containsKey("artist")) {
        prevMetadata.putString(METADATA_KEY_ARTIST, data.getString("artist"));
      }
      if (data.containsKey("album")) {
        prevMetadata.putString(METADATA_KEY_ALBUM, data.getString("album"));
      }
      if (data.containsKey("lyric")) {
        prevMetadata.putString("android.media.metadata.LYRICS", data.getString("lyric"));
      }
      session.setMetadata(prevMetadata.build());
      updatePlaybackState(manager.getPlayback());
    }`,
  `    public void updateTitles(Bundle data) {
      absorbBydLyric(data);
      if (prevMetadata == null) {
        ExoPlayback playback = manager.getPlayback();
        if (playback != null) updatePlaybackState(playback);
        return;
      }
      if (data.containsKey("title")) {
        prevMetadata.putString(METADATA_KEY_TITLE, data.getString("title"));
      }
      if (data.containsKey("artist")) {
        prevMetadata.putString(METADATA_KEY_ARTIST, data.getString("artist"));
      }
      if (data.containsKey("album")) {
        prevMetadata.putString(METADATA_KEY_ALBUM, data.getString("album"));
      }
      if (data.containsKey("lyric")) {
        String lyric = data.getString("lyric");
        prevMetadata.putString("android.media.metadata.LYRICS", lyric);
        prevMetadata.putString("${BYD_KEY}", lyric);
      }
      session.setMetadata(prevMetadata.build());
      updatePlaybackState(manager.getPlayback());
    }`,
  'updateTitles',
)

nextMetadata = replaceOnce(
  nextMetadata,
  `        MediaMetadataCompat.Builder metadata = track.toMediaMetadata();
        prevMetadata = metadata;`,
  `        MediaMetadataCompat.Builder metadata = track.toMediaMetadata();
        seedBydLyricFromTrack(track);
        applyBydLyricMetadata(metadata);
        prevMetadata = metadata;`,
  'updateMetadata',
)

nextMetadata = replaceOnce(
  nextMetadata,
  `        pb.setBufferedPosition(playback.getBufferedPosition());

        session.setPlaybackState(pb.build());`,
  `        pb.setBufferedPosition(playback.getBufferedPosition());
        attachBydLyricExtras(pb);

        session.setPlaybackState(pb.build());`,
  'playbackState',
)

const helpers = `
    // [BYD] 歌词字段。键名与 AnyLyric 在迪链上读到的一致：
    // extras.lyrics 是整段 LRC，lrc_line_1 是当前句，2/3 是后面两句。
    private void absorbBydLyric(Bundle data) {
        if (data.containsKey("lyric") || data.containsKey("lyrics")) {
            String lyric = data.containsKey("lyric") ? data.getString("lyric") : data.getString("lyrics");
            if (lyric == null) lyric = "";
            String previous = bydLyricExtras.getString("lyrics", "");
            if (!lyric.equals(previous)) {
                bydLyricExtras.remove("lrc_line_1");
                bydLyricExtras.remove("lrc_line_2");
                bydLyricExtras.remove("lrc_line_3");
            }
            bydLyricExtras.putString("lyrics", lyric);
        }
        copyBydLine(data, "lrc_line_1");
        copyBydLine(data, "lrc_line_2");
        copyBydLine(data, "lrc_line_3");
        if (data.containsKey("lyrics")) {
            String lyric = data.getString("lyrics");
            bydLyricExtras.putString("lyrics", lyric == null ? "" : lyric);
        }
    }

    private void copyBydLine(Bundle data, String key) {
        if (!data.containsKey(key)) return;
        String value = data.getString(key);
        bydLyricExtras.putString(key, value == null ? "" : value);
    }

    private void seedBydLyricFromTrack(TrackMetadata track) {
        if (track == null || track.lyric == null || track.lyric.isEmpty()) return;
        if (!bydLyricExtras.getString("lyrics", "").isEmpty()) return;
        bydLyricExtras.putString("lyrics", track.lyric);
    }

    private void applyBydLyricMetadata(MediaMetadataCompat.Builder metadata) {
        if (metadata == null || !bydLyricExtras.containsKey("lyrics")) return;
        String lyric = bydLyricExtras.getString("lyrics", "");
        metadata.putString("android.media.metadata.LYRICS", lyric);
        metadata.putString("${BYD_KEY}", lyric);
    }

    private void attachBydLyricExtras(PlaybackStateCompat.Builder playback) {
        if (bydLyricExtras.isEmpty()) return;
        playback.setExtras(new Bundle(bydLyricExtras));
    }
`

if (!nextMetadata.endsWith('}\n') && !nextMetadata.endsWith('}')) {
  fail('BYD lyric patch: MetadataManager.java does not end with a class close')
}
const closeAt = nextMetadata.lastIndexOf('}')
nextMetadata = `${nextMetadata.slice(0, closeAt)}${helpers}}\n`

let nextTrack = trackMetadata
nextTrack = replaceOnce(
  nextTrack,
  `        if (lyric != null) {
            builder.putString("android.media.metadata.LYRICS", lyric);
        }`,
  `        if (lyric != null) {
            builder.putString("android.media.metadata.LYRICS", lyric);
            builder.putString("${BYD_KEY}", lyric);
        }`,
  'track lyric key',
)

writeFileSync(metadataPath, nextMetadata)
writeFileSync(trackMetadataPath, nextTrack)
console.log('BYD lyric session patch applied')
