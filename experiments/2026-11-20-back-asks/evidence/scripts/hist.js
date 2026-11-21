JSON.stringify({at: navigation.currentEntry.index, pages: navigation.entries().map(e => new URL(e.url).pathname + new URL(e.url).search)})
