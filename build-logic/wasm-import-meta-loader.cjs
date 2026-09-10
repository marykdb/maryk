module.exports = function normalizeDirectImportMeta(source) {
    return source.replace(/(?<![.\w])import\.meta(?![.\w])/g, '({ url: import.meta.url })');
};
