lx.on('request', function (payload) {
  if (!payload || payload.action !== 'musicUrl') return Promise.reject(new Error('unsupported'))
  var info = payload.info && payload.info.musicInfo
  var id = info && (info.songmid || info.hash)
  if (id === 'public-tone') {
    return Promise.resolve('https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3')
  }
  return Promise.reject(new Error('no reviewed url'))
}).then(function () {
  var music = { type: 'music', actions: ['musicUrl'], qualitys: ['128k', '320k', 'flac', 'flac24bit'] }
  return lx.send('inited', { sources: { kw: music, kg: music, tx: music, wy: music, mg: music } })
})
